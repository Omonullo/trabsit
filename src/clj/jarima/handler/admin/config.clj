(ns jarima.handler.admin.config
  (:require [jarima.config :refer [mwage]]
            [jarima.validation :refer [validate-mwage]]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]
            [jarima.util :as util]))


(defn mwage-form
  [_]
  (layout/render
    "admin/config/mwage.html"
    {:value @mwage}))


(defn update-mwage
  [request]
  (let [params (-> request :parameters :form)]
    (when-let [errors (validate-mwage params)]
      (layout/error-page!
        {:status 400
         :title  (t "Плохой запрос")
         :data   (util/pretty-errors errors)}))
    (reset! mwage (:value params))
    (-> (util/route-path request :admin.config/mwage)
        (response/found))))


(def routes
  ["/config"
   ["/minimum-wage"
    {:name       :admin.config/mwage
     :get        #'mwage-form
     :post       #'update-mwage
     :parameters {:form :admin.config.mwage/form}}]])
