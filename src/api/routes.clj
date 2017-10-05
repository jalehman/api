(ns api.routes
  (:require [blueprints.models.account :as account]
            [blueprints.models.order :as order]
            [blueprints.models.service :as service]
            [compojure.core :refer [context defroutes GET POST]]
            [api.config :as config :refer [config]]
            [api.deps :as deps]
            [api.slack :as slack]
            [cheshire.core :as json]
            [clj-slack.users :as slack-users]
            [org.httpkit.client :as http]
            [taoensso.timbre :as timbre]
            [ring.util.response :as response]
            [ring.util.codec :as codec]
            [api.slack.message :as sm]
            [datomic.api :as d]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Global
;; =============================================================================

;; Registry of currently active wishes.
(defonce active-wishes
  (atom {}))


(defonce access-token
  (atom nil))


(def ^:private concierge-scopes
  ["commands" "users:read" "users:read.email"])


;; =============================================================================
;; Helpers
;; =============================================================================


(defn- valid-request? [verification-token params]
  (= (:token params) verification-token))


(defn user-id->account
  "Given a Slack `user-id`, find the associated account in our system."
  [db slack user-id]
  (let [user (slack-users/info slack user-id)]
    (d/entity db [:account/email (get-in user [:user :profile :email])])))


(defn- concierge-conn [deps]
  (api.slack/conn @access-token))


(defn concierge-dispatch-id->concierge-id
  [deps user-id]
  (let [team-users      (:members (slack-users/list (deps/->concierge-dispatch deps)))
        community-users (:members (slack-users/list (concierge-conn deps)))
        team-user       (tb/find-by #(= user-id (:id %)) team-users)
        community-user  (tb/find-by
                         #(= (get-in team-user [:profile :email])
                             (get-in % [:profile :email]))
                         community-users)]
    (:id community-user)))


(defn send-slack-message [url body]
  @(http/post url {:headers {"Content-Type" "application/json"}
                   :body    (json/generate-string body)}))


;; =============================================================================
;; Concierge Dispatch
;; =============================================================================


(defn- concierge-dispatch-body
  [account body trigger-id]
  (letfn [(-button-value [v]
            (pr-str [v [(:db/id account) trigger-id]]))]
    (sm/msg
     (sm/info
      (sm/title (format "%s %s made a wish!"
                        (account/first-name account) (account/last-name account)))
      (sm/text "_" body "_")
      (sm/callback "wish_fulfill")
      (sm/actions
       (sm/button "fulfill" "Accept" (-button-value "accept"))
       (sm/button "fulfill" "Reject" (-button-value "reject")))))))


(defmulti concierge-dispatch-callback
  "Handler for concierge dispatch callbacks."
  (fn [deps params]
    (:callback_id params)))


(defmethod concierge-dispatch-callback :default
  [_ {:keys [callback_id] :as params}]
  (timbre/warn :concierge-dispatch-callback/unrecognized {:callback callback_id
                                                          :params   params})
  [404 (format "Callback '%s' not found." callback_id)])


(defn- send-confirmation!
  [deps user-id response-url fmt]
  (let [concierge-user-id (concierge-dispatch-id->concierge-id deps user-id)]
    (->> {:text (format fmt (sm/user concierge-user-id))}
         (send-slack-message response-url))))


(def ^:private confirmation-strings
  {"accept" "Your wish has been granted! Expect %s to reach out soon."
   "reject" "Your wish has been rejected by %s."})


(def ^:private report-color
  {"accept" sm/green
   "reject" sm/red})


(defn- resolve-wish!
  [deps account user-id result {:keys [wish-text response-url]}]
  (send-confirmation! deps user-id response-url (get confirmation-strings result))
  (sm/msg
   (sm/attachment
    (get report-color result)
    (sm/title (format "%s's wish has been %sed by %s."
                      (account/full-name account)
                      result
                      (sm/user user-id)))
    (sm/text "_" wish-text "_"))))


(defmethod concierge-dispatch-callback "wish_fulfill"
  [deps {:keys [trigger_id actions response_url user]}]
  (let [[result wish-path] (-> actions first :value read-string)
        account            (d/entity (deps/->db deps) (first wish-path))
        service            (service/by-code (deps/->db deps) "wish")]
    (if-let [params (get-in @active-wishes wish-path)]
      (do
        (if (= "accept" result)
          @(d/transact (deps/->conn deps) [(order/create account service {:desc (:wish-text params)})])
          (swap! active-wishes tb/dissoc-in wish-path))
        [200 (resolve-wish! deps account (:id user) result params)])
      [422 "Something went wrong; couldn't find the wish. It was likely already processed."])))


(defn handle-concierge-dispatch-callback
  [{:keys [params] :as req}]
  (let [params (-> params :payload (json/parse-string true))
        config (deps/->config req)]
    (if (valid-request? (config/slack-concierge-dispatch-verification config) params)
      (let [[status body] (concierge-dispatch-callback (deps/->deps req) params)]
        (-> (response/response body)
            (response/status status)
            (response/content-type "application/json")))
      (-> (response/response "unauthorized")
          (response/status 403)))))


(defn send-concierge-dispatch-msg [deps account text trigger-id]
  (->> (concierge-dispatch-body account text trigger-id)
       (send-slack-message (config/slack-concierge-dispatch-webhook (deps/->config deps)))))


;; =============================================================================
;; Concierge
;; =============================================================================


(defmulti concierge
  "Handler for Starcity Concierge slash commands."
  (fn [deps params]
    (:command params)))


(defmethod concierge :default [_ params]
  (timbre/warn :concierge/unrecognized {:command (:command params)
                                        :params  params})
  [404 (format "Command '%s' not found." (:command params))])


(defn dispatch-wish
  [deps account {:keys [text trigger_id response_url] :as params}]
  (swap! active-wishes assoc-in [(:db/id account) trigger_id] {:response-url response_url
                                                               :wish-text    text})
  (send-concierge-dispatch-msg deps account text trigger_id))


(defn- wish-received-message [account]
  (format "Thanks for submitting your wish, %s! I've sent it to the team, and I'll let you know as soon as I have more information."
          (account/first-name account)))


(defmethod concierge "/wish"
  [deps params]
  (if-let [account (user-id->account (deps/->db deps) (concierge-conn deps) (:user_id params))]
    (do
      (dispatch-wish deps account params)
      [200 (wish-received-message account)])
    (do
      (timbre/error :concierge/account-not-found {:params params})
      [403 "We do not have your account on file."])))


(defn- handle-concierge-request
  [{:keys [params] :as req}]
  (if (valid-request? (config/slack-concierge-verification (deps/->config req)) params)
    (let [[status body] (concierge (deps/->deps req) params)]
      (-> (response/response body)
          (response/status status)
          (response/content-type "application/json")))
    (do
      (timbre/error :concierge/unauthorized params)
      (-> (response/response "unauthorized")
          (response/status 403)))))


(defn- handle-oauth [req]
  (let [config    (deps/->config req)
        client-id (config/slack-concierge-client-id config)
        scopes    (->> concierge-scopes (interpose " ") (apply str))]
    (-> (format "https://slack.com/oauth/authorize?client_id=%s&scope=%s&redirect_uri=%s"
                (codec/url-encode client-id)
                (codec/url-encode scopes)
                "https://api.joinstarcity.com/oauth/redirect")
        (response/redirect))))


(defn- handle-oauth-redirect [{:keys [params] :as req}]
  (let [config        (deps/->config req)
        client-id     (config/slack-concierge-client-id config)
        client-secret (config/slack-concierge-client-secret config)
        response      @(http/get "https://slack.com/api/oauth.access"
                                 {:headers      {"Content-Type" "application/json"}
                                  :query-params {:client_id     client-id
                                                 :client_secret client-secret
                                                 :code          (:code params)
                                                 :redirect_uri  "https://api.joinstarcity.com/oauth/redirect"}})]
    (timbre/info ::access-response response)
    (reset! access-token (-> response :body (json/parse-string true) :access_token))
    (response/redirect "https://joinstarcity.com")))


;; =============================================================================
;; Routes
;; =============================================================================


(defroutes routes
  (GET "/oauth" [] handle-oauth)

  (GET "/oauth/redirect" [] handle-oauth-redirect)

  (POST "/slack/concierge" [] handle-concierge-request)

  (POST "/slack/concierge/dispatch" [] handle-concierge-dispatch-callback))


(comment
  (select-keys
   @(http/post "https://api.joinstarcity.com/slack/concierge"
               {:form-params mock-request})
   [:body :status])

  )
