(ns jarima.handler.admin.staff
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.util :as util]
    [jarima.db.core :as db]
    [clojure.string :as str]
    [medley.core :refer :all]
    [jarima.db.query :as query]
    [jarima.db.query :as query]
    [buddy.hashers :as hashers]
    [jarima.layout :as layout :refer [t]]
    [medley.core :refer [update-existing]]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]))


(defn clean
  "Nullifies area_id and district_id keys and makes staff active if its role is not inspector"
  [staff]
  (cond-> staff
    (not= "inspector" (:role staff)) (assoc :area_id nil :district_id nil :active true)
    :always (update-existing :username #(some-> % str/lower-case))))


(defn index
  [{{query :query} :parameters}]
  (layout/render
    "admin/staff/list.html"
    (-> (util/paged-query
          (partial query/select-staff (util/vectorify-vals query (complement #{:name})))
          (:page query)))))


(defn create-form
  [_]
  (layout/render
    "admin/staff/form.html"))


(defn create
  [request]
  (-> (query/insert-staff
        (-> request :parameters :form
            (update :password hashers/derive)
            (update :active boolean)
            (update :two_factor_enabled boolean)
            (update :review_allowed boolean)
            (assoc :id (random-uuid))
            (clean)))
      (db/query))
  (-> (util/route-path request :admin.staff/index)
      (response/found)))


(defn edit-form
  [request]
  (let [staff
        (-> (query/select-staff {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not staff
      (layout/error-page!
        {:status 404
         :title  (t "Пользователь не найден")}))
    (layout/render
      "admin/staff/form.html"
      {:staff staff})))


(defn edit
  [request]
  (let [staff
        (-> (query/select-staff {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not staff
      (layout/error-page!
        {:status 404
         :title  (t "Пользователь не найден")}))
    (-> (query/update-staff
          (:id staff)
          (-> request :parameters :form
              (dissoc :username)
              (update :active boolean)
              (update :two_factor_enabled boolean)
              (update :review_allowed boolean)
              (update :password #(if % (hashers/derive %) (:password staff)))
              (clean)))
        (db/query))
    (-> (util/route-path request :admin.staff/index)
        (response/found))))


(defn delete
  [request]
  (-> (util/route-path request :admin.staff/index)
      (response/found)
      (assoc :flash
             (when (zero? (db/delete :staff (-> request :parameters :path :id)))
               {:alert {:warning (t "Ну удалось удалить пользователя")}}))))


(def routes
  ["/staff"
   [""
    {:name       :admin.staff/index
     :get        #'index
     :parameters {:query :admin.staff.index/query}}]

   ["/new"
    {:name :admin.staff/create
     :get  #'create-form
     :post #'create
     :parameters {:form :admin.staff/form}}]

   ["/:id/edit"
    {:name       :admin.staff/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/uuid}
                  :form :admin.staff/form}}]

   ["/:id/delete"
    {:name       :admin.staff/delete
     :post       #'delete
     :parameters {:path {:id :jarima.spec/uuid}}}]])
