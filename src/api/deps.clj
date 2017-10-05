(ns api.deps
  (:require [datomic.api :as d]))


(defn ->deps
  "Extract the dependencies from map `m`. If `m` has a `:deps` key, those are
  the dependencies; otherwise, treat the entire map as the dependencies."
  [m]
  (or (:deps m) m))

(def ->conn (comp :conn ->deps))

(def ->db (comp d/db ->conn))

(def ->config (comp :config ->deps))

(def ->concierge-dispatch (comp :concierge-dispatch ->deps))
