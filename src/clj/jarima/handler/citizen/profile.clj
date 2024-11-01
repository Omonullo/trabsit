(ns jarima.handler.citizen.profile
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.util :as util]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [medley.core :refer :all]
    [jarima.layout :as layout :refer [t]]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [jarima.ffmpeg :refer [extract-thumbnail]]
    [jarima.validation :refer [validate-profile-edit]]))



(defn edit-form [request]
  (layout/render
    "citizen/profile.html"
    {:banks   (:banks dictionary)
     :citizen (:identity request)}))


(defn edit [request]
  (let [params (-> request :parameters :form)]
    (when-let [errors (validate-profile-edit params)]
      (layout/error-page!
        {:status 400
         :title  (t "Плохой запрос")
         :data   (util/pretty-errors errors)}))
    (-> (q/update-citizen {:id [(-> request :identity :id)]} params)
        (db/query)
        (first))
    (-> (util/route-path request :citizen.report/index)
        (response/found))))



(def routes
  ["/profile"
   {:name       :citizen.profile/edit
    :get        #'edit-form
    :post       #'edit
    :parameters {:form :citizen.profile/form}}])
