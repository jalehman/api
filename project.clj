(defproject api "0.1.0-SNAPSHOT"
  :description "Starcity API server."
  :url "http://api.joinstarcity.com"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/tools.cli "0.3.5"]
                 [http-kit "2.2.0"]
                 [compojure "1.6.0"]
                 [ring/ring "1.6.2"]
                 [ring-middleware-format "0.7.2"]
                 [buddy "1.3.0"]
                 [bouncer "1.0.1"]
                 [starcity/datomic-session-store "0.1.0"]
                 [starcity/customs "0.1.0" :exclusions [starcity/toolbelt]]
                 [org.julienxx/clj-slack "0.5.5"]
                 [clj-time "0.13.0"]
                 [org.apache.httpcomponents/httpclient "4.5.3"]
                 [mount "0.1.11"]
                 [starcity/blueprints "1.13.0-SNAPSHOT" :exclusions [com.datomic/datomic-free starcity/toolbelt]]
                 [aero "1.1.2"]
                 [starcity/drawknife "0.2.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [starcity/toolbelt "0.1.9-SNAPSHOT" :exclusions [com.datomic/datomic-free]]
                 [cheshire "5.8.0"]]

  :jvm-opts ["-server"
             "-Xmx2g"
             "-XX:+UseCompressedOops"
             "-XX:+DoEscapeAnalysis"
             "-XX:+UseConcMarkSweepGC"]

  :repositories {"releases" {:url        "s3://starjars/releases"
                             :username   :env/aws_access_key
                             :passphrase :env/aws_secret_key}}

  :plugins [[s3-wagon-private "1.2.0"]]

  :repl-options {:init-ns user}

  :main api.core)
