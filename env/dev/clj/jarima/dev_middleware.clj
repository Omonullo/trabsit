(ns jarima.dev-middleware
  (:require [prone.middleware :refer [wrap-exceptions]]
            [selmer.middleware :refer [wrap-error-page]]
            [compojure.route :refer [files]]))


(defn wrap-file
  [handler]
  (let [files-handler (files "/npm" {:root "node_modules"})]
    (fn [request]
      (or (files-handler request)
          (handler request)))))


(defn wrap-dev [handler]
  (-> handler
      (wrap-file)
      (wrap-exceptions {:app-namespaces ['jarima]})))
