(ns jarima.handler.admin.offense-type
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require [jarima.util :as util]
            [jarima.db.core :as db]
            [medley.core :refer :all]
            [jarima.validation :as v]
            [jarima.db.query :as query]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]))


(defn index
  [_]
  (layout/render
    "admin/offense-type/list.html"
    {:new           true
     :offense-types (-> (query/select-offense-type nil)
                        (db/query))}))


(defn create-form
  [_]
  (layout/render "admin/offense-type/form.html" {:creation true}))


(defn create
  [request]
  (let [offense-type (-> request :parameters :form)]
    (if-let [errors (v/validate-offense-type offense-type)]
      (layout/render "admin/offense-type/form.html"
                     {:offense-type offense-type
                      :errors       errors
                      :creation     true})
      (do
        (-> (query/insert-offense-type offense-type)
            (db/query))
        (-> (util/route-path request :admin.offense-type/index)
            (response/found))))))


(defn edit-form
  [request]
  (let [offense-type (-> (query/select-offense-type {:id [(-> request :parameters :path :id)]})
                         (db/query-first))]
    (when-not offense-type
      (layout/error-page!
        {:status 404
         :title  (t "Тип нарушения не найден")}))
    (layout/render
      "admin/offense-type/form.html"
      {:offense-type offense-type})))


(defn edit
  [request]
  (let [offense-type (-> request :parameters :form)
        id (-> request :parameters :path :id)]
    (when-not (-> (query/select-offense-type {:id [id]})
                  (db/query-first :id))
      (layout/error-page!
        {:status 404
         :title  (t "Тип нарушения не найден")}))
    (if-let [errors (v/validate-offense-type (assoc offense-type :old-id id))]
      (layout/render "admin/offense-type/form.html"
                     {:offense-type offense-type
                      :errors       errors})
      (do (-> (query/update-offense-type id offense-type)
              (db/query))
          (-> (util/route-path request :admin.offense-type/index)
              (response/found))))))



(defn delete
  [request]
  (-> (util/route-path request :admin.offense-type/index)
      (response/found)
      (assoc :flash
             (when (zero? (db/delete :offense_type (int (-> request :parameters :path :id))))
               {:alert {:warning (t "Ну удалось удалить статью")}}))))


(def routes
  ["/offense-types"
   [""
    {:name :admin.offense-type/index
     :get  #'index}]

   ["/new"
    {:name       :admin.offense-type/create
     :get        #'create-form
     :post       #'create
     :parameters {:form :admin.offense-type/form}}]

   ["/:id/edit"
    {:name       :admin.offense-type/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :offense-type/id}
                  :form :admin.offense-type/form}}]

   ["/:id/delete"
    {:name       :admin.offense-type/delete
     :post       #'delete
     :parameters {:path {:id :offense-type/id}}}]])
