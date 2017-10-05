(ns api.slack
  (:require [api.config :as config :refer [config]]
            [mount.core :refer [defstate]]))


(defn conn [token]
  {:api-url "https://slack.com/api"
   :token   token})


(defstate concierge-dispatch-conn
  :start {:api-url "https://slack.com/api"
          :token   (config/slack-concierge-dispatch-token config)})
