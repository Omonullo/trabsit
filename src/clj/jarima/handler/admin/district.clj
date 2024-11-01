(ns jarima.handler.admin.district
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require [jarima.db.core :as db]
            [jarima.db.query :as query]
            [medley.core :refer :all]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]
            [jarima.util :as util]))


(defn index
  [_]
  (layout/render
    "admin/district/list.html"
    {:districts (-> (query/select-district nil)
                    (db/query))}))


(defn create-form
  [_]
  (layout/render
    "admin/district/form.html"
    {:areas (-> (query/select-area nil)
                (db/query))}))


(defn create
  [request]
  (-> (query/insert-district (-> request :parameters :form (assoc :id (random-uuid))))
      (db/query))
  (-> (util/route-path request :admin.district/index)
      (response/found)))


(defn edit-form
  [request]
  (let [district
        (-> (query/select-district {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not district
      (layout/error-page!
        {:status 404
         :title  (t "Район не найден")}))
    (layout/render
      "admin/district/form.html"
      {:district district
       :areas    (-> (query/select-area nil)
                     (db/query))})))


(defn edit
  [request]
  (let [district
        (-> (query/select-district {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not district
      (layout/error-page!
        {:status 404
         :title  (t "Район не найден")}))
    (-> (query/update-district (:id district) (-> request :parameters :form (update :obsolete boolean)))
        (db/query))
    (-> (util/route-path request :admin.district/index)
        (response/found))))


(defn delete
  [request]
  (-> (util/route-path request :admin.district/index)
      (response/found)
      (assoc :flash
             (when (zero? (db/delete :district (-> request :parameters :path :id)))
               {:alert {:warning (t "Ну удалось удалить район")}}))))


(def routes
  ["/districts"
   [""
    {:name :admin.district/index
     :get  #'index}]

   ["/new"
    {:name       :admin.district/create
     :get        #'create-form
     :post       #'create
     :parameters {:form :admin.district/form}}]

   ["/:id/edit"
    {:name       :admin.district/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/uuid}
                  :form :admin.district/form}}]

   ["/:id/delete"
    {:name       :admin.district/delete
     :post       #'delete
     :parameters {:path {:id :jarima.spec/uuid}}}]])
