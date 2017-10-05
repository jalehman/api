(ns api.datomic
  (:require [api.config :as config :refer [config]]
            [datomic.api :as d]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [blueprints.core :as blueprints]
            [clojure.string :as string]))


(defn- scrub-uri [uri]
  (string/replace uri #"password.*" ""))


(defn- new-connection [uri]
  (timbre/info ::connecting {:uri (scrub-uri uri)})
  (d/create-database uri)
  (let [conn (d/connect uri)]
    (blueprints/conform-db conn :db.part/starcity)
    conn))


(defn- disconnect [uri conn]
  (timbre/info ::disconnecting {:uri (scrub-uri uri)})
  (d/release conn))


(defstate conn
  :start (new-connection (config/datomic-uri config))
  :stop (disconnect (config/datomic-uri config) conn))
