(ns jarima.handler.admin.area
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require [jarima.db.core :as db]
            [jarima.db.query :as query]
            [medley.core :refer :all]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]
            [jarima.util :as util]))


(comment
  (honeysql.core/format
    (query/select-report {:age {:gt 3 :lt 5}}))

  (db/query
    {:select [(honeysql.core/call :diff-days (.minusDays (util/today) 60) :%now)]
     :where  [:!= nil [:date-trunc :day [:cast (util/today) :date]]]})

  (db/with-debug
    (take 10
      (db/query
        (query/offense-stats :area {:date_range {:lt  (util/today)
                                                 :gte (.minusDays (util/today) 7)}}))))

  (db/with-debug
    (take 10
          (db/query
            (query/offenses-funnel :area {:today      (util/today)
                                          :date_range {:lt  (util/today)
                                                       :gte (.minusDays (util/today) 7)}})))))


(defn index
  [_]
  (layout/render
    "admin/area/list.html"
    {:areas (-> (query/select-area nil)
                (db/query))}))


(defn create-form
  [_]
  (layout/render
    "admin/area/form.html"
    {}))


(defn create
  [request]
  (-> (query/insert-area (-> request :parameters :form (assoc :id (random-uuid))))
      (db/query))
  (-> (util/route-path request :admin.area/index)
      (response/found)))


(defn edit-form
  [request]
  (let [area
        (-> (query/select-area {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not area
      (layout/error-page!
        {:status 404
         :title  (t "Область не найден")}))
    (layout/render
      "admin/area/form.html"
      {:area      area})))


(defn edit
  [request]
  (let [area
        (-> (query/select-area {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not area
      (layout/error-page!
        {:status 404
         :title  (t "Область не найден")}))
    (-> (query/update-area (:id area) (-> request :parameters :form (update :obsolete boolean)))
        (db/query))
    (-> (util/route-path request :admin.area/index)
        (response/found))))


(defn delete
  [request]
  (-> (util/route-path request :admin.area/index)
      (response/found)
      (assoc :flash
             (when (zero? (db/delete :area (-> request :parameters :path :id)))
               {:alert {:warning (t "Ну удалось удалить область")}}))))


(def routes
  ["/areas"
   [""
    {:name :admin.area/index
     :get  #'index}]

   ["/new"
    {:name       :admin.area/create
     :get        #'create-form
     :post       #'create
     :parameters {:form :admin.area/form}}]

   ["/:id/edit"
    {:name       :admin.area/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/uuid}
                  :form :admin.area/form}}]

   ["/:id/delete"
    {:name       :admin.area/delete
     :post       #'delete
     :parameters {:path {:id :jarima.spec/uuid}}}]])
