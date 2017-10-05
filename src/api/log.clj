(ns api.log
  (:require [api.config :as config :refer [config]]
            [drawknife.core :as drawknife]
            [taoensso.timbre :as timbre]
            [mount.core :refer [defstate]]))


(defstate logger
  :start (timbre/merge-config!
          (drawknife/configuration (config/log-level config)
                                   (config/log-appender config)
                                   (config/log-file config))))
