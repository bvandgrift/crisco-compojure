(ns crisco.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.params :as p]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]))

(defroutes app
  (GET "/" []
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (-> "index.html"
                  io/resource
                  slurp
                  )})
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))



(defn wrap-app [app]
  (-> app
        ((if (env :production)
           wrap-error-page
           trace/wrap-stacktrace))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (wrap-app #'app) {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
