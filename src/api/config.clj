(ns api.config
  (:require [aero.core :as aero]
            [mount.core :as mount :refer [defstate]]
            [clojure.java.io :as io]
            [toolbelt.core :as tb]))

;; =============================================================================
;; State
;; =============================================================================


(defstate config
  :start (-> (io/resource "config.edn")
             (aero/read-config {:resolver aero/root-resolver
                                :profile  (:env (mount/args))})))


;; =============================================================================
;; Web Server
;; =============================================================================


(defn webserver-port
  "Produce the port that the webserver should start on."
  [config]
  (tb/str->int (get-in config [:webserver :port])))


(defn cookie-name
  "Session cookie name."
  [config]
  (get-in config [:webserver :cookie-name]))


(defn secure-sessions?
  "Should session cookies be secure?"
  [config]
  (get-in config [:webserver :secure-sessions]))


;; =============================================================================
;; Domain
;; =============================================================================


(defn root-domain
  "The top-level domain of the application."
  [config]
  (:root-domain config))


;; =============================================================================
;; Datomic
;; =============================================================================


(defn datomic
  "The Datomic configuration. Contains `:uri` and `:partition`"
  [config]
  (:datomic config))


(defn datomic-uri
  "URI of the Datomic database connection."
  [config]
  (get-in config [:datomic :uri]))


;; =============================================================================
;; nrepl
;; =============================================================================


(defn nrepl-port
  "Port to run the nrepl server on."
  [config]
  (tb/str->int (get-in config [:nrepl :port])))


;; =============================================================================
;; Stripe
;; =============================================================================


(defn stripe-public-key
  "The Stripe public key."
  [config]
  (get-in config [:secrets :stripe :public-key]))


(defn stripe-secret-key
  "The Stripe secret key."
  [config]
  (get-in config [:secrets :stripe :secret-key]))


;; =============================================================================
;; Slack
;; =============================================================================


(defn slack-concierge-client-id
  [config]
  (get-in config [:secrets :slack :concierge :client-id]))


(defn slack-concierge-client-secret
  [config]
  (get-in config [:secrets :slack :concierge :client-secret]))


(defn slack-concierge-verification
  [config]
  (get-in config [:secrets :slack :concierge :verification]))


(defn slack-concierge-dispatch-token
  [config]
  (get-in config [:secrets :slack :concierge-dispatch :token]))


(defn slack-concierge-dispatch-verification
  [config]
  (get-in config [:secrets :slack :concierge-dispatch :verification]))


(defn slack-concierge-dispatch-webhook
  [config]
  (get-in config [:secrets :slack :concierge-dispatch :webhook]))


;; =============================================================================
;; Environments
;; =============================================================================


(defn development? [config]
  (= :dev (:env (mount/args))))


(defn staging? [config]
  (= :stage (:env (mount/args))))


(defn production? [config]
  (= :prod (:env (mount/args))))


;; =============================================================================
;; Log
;; =============================================================================


(defn log-level
  [config]
  (get-in config [:log :level]))


(defn log-appender
  [config]
  (get-in config [:log :appender]))


(defn log-file
  [config]
  (get-in config [:log :file]))
