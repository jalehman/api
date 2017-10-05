(ns api.server
  (:require [api.config :as config :refer [config]]
            [api.datomic :refer [conn]]
            [api.routes :as routes]
            [api.slack :as slack]
            [buddy.auth :as buddy]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [clojure.string :as string]
            [customs.access :as access]
            [mount.core :refer [defstate]]
            [org.httpkit.server :as httpkit]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.datomic :refer [datomic-store session->entity]]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))


(defn wrap-logging
  "Middleware to log requests."
  [handler]
  (fn [{:keys [uri request-method session remote-addr params] :as req}]
    (timbre/info :web/request
                 (tb/assoc-when
                  {:uri         uri
                   :method      request-method
                   :remote-addr remote-addr
                   :params      params}
                  :user (get-in session [:identity :account/email])))
    (handler req)))


(defn wrap-exception-handling
  [handler]
  (fn [{:keys [session uri request-method remote-addr] :as req}]
    (try
      (handler req)
      (catch Throwable t
        (do
          (timbre/error t ::error (tb/assoc-when
                                   {:uri         uri
                                    :method      request-method
                                    :remote-addr remote-addr}
                                   :user (get-in session [:identity :account/email])))
          {:status 500
           :body   "Unexpected server error!"})))))


(defn- unauthorized-handler
  [request metadata]
  (let [[status body] (if (buddy/authenticated? request)
                        [403 "unauthorized"]
                        [401 "unauthenticated"])]
    (-> (response/response body)
        (response/status status)
        (response/content-type "application/json"))))


(def ^:private auth-backend
  (access/auth-backend :unauthorized-handler unauthorized-handler))


(defn- wrap-deps [handler deps]
  (fn [req]
    (handler (assoc req :deps deps))))


(defn app-handler [deps]
  (-> routes/routes
      (wrap-deps deps)
      (wrap-authorization auth-backend)
      (wrap-authentication auth-backend)
      (wrap-logging)
      (wrap-keyword-params)
      (wrap-nested-params)
      (wrap-restful-format)
      (wrap-params)
      (wrap-multipart-params)
      (wrap-resource "public")
      (wrap-session {:store        (datomic-store (:conn deps) :session->entity session->entity)
                     :cookie-name  (config/cookie-name config)
                     :cookie-attrs {:secure (config/secure-sessions? config)}})
      (wrap-exception-handling)
      (wrap-content-type)
      (wrap-not-modified)))


;; =============================================================================
;; State
;; =============================================================================


(defn- start-server [port handler]
  (timbre/info ::starting {:port port})
  (httpkit/run-server handler {:port port :max-body (* 20 1024 1024)}))


(defn- stop-server [server]
  (timbre/info ::stopping)
  (server))


(defstate web-server
  :start (->> (app-handler {:conn               conn
                            :config             config
                            :concierge-dispatch slack/concierge-dispatch-conn})
              (start-server (config/webserver-port config)))
  :stop (stop-server web-server))
