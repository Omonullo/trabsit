(ns jarima.handler.citizen.organization
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.db.core :as db]
    [medley.core :refer :all]
    [jarima.layout :as layout]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [jarima.ffmpeg :refer [extract-thumbnail]]
    [jarima.util :as util]))


(defn index
  [request]
  (layout/render "citizen/organization/list.html"
                 {:organizations (-> {:select [:*]
                                      :from   [:organization]
                                      :where  [:= :citizen_id (-> request :identity :id)]}
                                     (db/query (fn [organization]
                                                 (-> organization
                                                     (assoc :area (-> organization :area_id layout/area-name))
                                                     (assoc :district (-> organization :district_id layout/district-name))))))}))


(defn create-form
  [request]
  (layout/render "citizen/organization/form.html"
                 {:types (:organization-types dictionary)}))


(defn create
  [request]
  (let [params (:params request)]
    (conman.core/with-transaction [db/*db*]
      (-> {:insert-into :organization
           :returning   [:*]
           :values      [(-> params
                             (select-keys [:citizen_id :name :inn :type :area_id :district_id :address :zipcode :bank_account :bank_mfo])
                             (update :area_id #(try (uuid %) (catch Throwable _ nil)))
                             (update :district_id #(try (uuid %) (catch Throwable _ nil)))
                             (assoc :citizen_id (-> request :identity :id))
                             (assoc :id (random-uuid)))]}
          (db/query)
          (first)))
    (-> (util/route-path request :citizen.organization/index)
        (response/found))))


(defn edit-form [request]
  (let [organization
        (-> {:select [:*]
             :from   [:organization]
             :where  [:and
                      [:= :id (-> request :parameters :path :id)]
                      [:= :citizen_id (-> request :identity :id)]]}
            (db/query)
            (first))]
    (when-not organization
      (layout/error-page
        {:status 404
         :title  "Юрлицо не найдена"}))
    (layout/render
      "citizen/organization/form.html"
      {:organization organization
       :types        (:organization-types dictionary)})))



(defn edit [request]
  (let [organization
        (-> {:select [:*]
             :from   [:organization]
             :where  [:and
                      [:= :id (-> request :parameters :path :id)]
                      [:= :citizen_id (-> request :identity :id)]]}
            (db/query)
            (first))]
    (when-not organization
      (layout/error-page!
        {:status 404
         :title  "Юрлицо не найдена"}))
    (-> {:update    :organization
         :set       (-> (:params request)
                        (select-keys [:citizen_id :name :inn :type :area_id :district_id :address :zipcode :bank_account :bank_mfo])
                        (update :area_id #(try (uuid %) (catch Throwable _ nil)))
                        (update :district_id #(try (uuid %) (catch Throwable _ nil))))
         :where     [:= :id (:id organization)]
         :returning [:*]}
        (db/query)
        (first))
    (-> (util/route-path request :citizen.organization/index)
        (response/found))))



(def routes
  ["/organizations"
   [""
    {:name :citizen.organization/index
     :get  #'index}]

   ["/new"
    {:name :citizen.organization/create
     :get  #'create-form
     :post #'create}]

   ["/:id/edit"
    {:name       :citizen.organization/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/uuid}}}]])
