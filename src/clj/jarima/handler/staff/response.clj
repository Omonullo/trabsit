(ns jarima.handler.staff.response
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require [jarima.util :as util]
            [jarima.db.core :as db]
            [medley.core :refer :all]
            [jarima.db.query :as query]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]))


(defn index
  [_]
  (layout/render
    "staff/response/list.html"
    {:responses (-> (query/select-response nil)
                    (db/query))}))


(defn create-form
  [_]
  (layout/render "staff/response/form.html"
    {:total_count (-> {:select [:%max.priority]
                       :from   [:response]}
                      (db/query-first :max)
                      (or 0)
                      (inc))}))


(defn create
  [request]
  (-> (query/insert-response (-> request :parameters :form
                                 (assoc :id (random-uuid))
                                 (update :obsolete boolean)))
      (db/query))
  (-> (util/route-path request :staff.response/index)
      (response/found)))


(defn edit-form
  [request]
  (let [response
        (-> (query/select-response {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not response
      (layout/error-page!
        {:status 404
         :title  (t "Причина отклонения не найден")}))
    (layout/render
      "staff/response/form.html"
      {:response response})))


(defn edit
  [request]
  (let [response
        (-> (query/select-response {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not response
      (layout/error-page
        {:status 404
         :title  (t "Причина отклонения не найден")}))
    (-> (query/update-response (:id response) (-> request :parameters :form
                                                  (update :obsolete boolean)))
        (db/query))
    (-> (util/route-path request :staff.response/index)
        (response/found))))


(defn delete
  [request]
  (-> (util/route-path request :staff.response/index)
      (response/found)
      (assoc :flash
             (when (zero? (db/delete :response (-> request :parameters :path :id)))
               {:alert {:warning (t "Ну удалось удалить причину отклонения")}}))))


(def routes
  ["/responses"
   [""
    {:name :staff.response/index
     :get  #'index}]

   ["/new"
    {:name       :staff.response/create
     :get        #'create-form
     :post       #'create
     :parameters {:form :staff.response/form}}]

   ["/:id/edit"
    {:name       :staff.response/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/uuid}
                  :form :staff.response/form}}]

   ["/:id/delete"
    {:name       :staff.response/delete
     :post       #'delete
     :parameters {:path {:id :jarima.spec/uuid}}}]])
