(defproject crisco "1.0.0-SNAPSHOT"
  :description "a project for url shortening"
  :url "http://github.com/bvandgrift/crisco"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [ring/ring-devel "1.2.2"]
                 [environ "0.5.0"]]
  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.2.1"]
            [lein-ring "0.8.0"]]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "crisco-standalone.jar"
  :profiles {:production {:env {:production true}}}
  :ring {:handler crisco.web/app}
  :main crisco.web)
