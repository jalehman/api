{:dev {:source-paths ["src/clj" "env/dev"]
       :dependencies [[com.datomic/datomic-free "0.9.5544"]]}

 :uberjar {:aot          :all
           :main         api.core
           :dependencies [[com.datomic/datomic-pro "0.9.5544"]
                          [org.postgresql/postgresql "9.4.1211"]]
           :source-paths ["src/clj"]
           :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                            :username :env/datomic_username
                                            :password :env/datomic_password}}}}
