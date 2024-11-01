(ns jarima.prod-middleware
  (:require [compojure.route :refer [resources]]))


(defn wrap-resource
  [handler]
  (let [resources-handler (resources "/npm" {:root "node_modules"})]
    (fn [request]
      (or (resources-handler request)
          (handler request)))))


(defn wrap-prod [handler]
  (-> handler
      (wrap-resource)))
