(ns jarima.handler.api.citizen
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.util :as util]
    [medley.core :refer :all]
    [jarima.reward :refer [reward-type]]
    [ring.util.http-response :as response]
    [conman.core :refer [with-transaction]]
    [jarima.config :refer [dictionary env]]
    [jarima.asbt :refer [offense->short-id]]
    [jarima.ffmpeg :refer [extract-thumbnail]]
    [jarima.layout :as layout :refer [t-get t]]
    [jarima.handler.staff.report :refer [video-source]]
    [jarima.validation :refer [validate-create-report validate-update-report validate-video-upload]]
    [jarima.validation :as v]
    [jarima.db.query :as q]
    [jarima.db.core :as db]))


(defn create-handler
  [request]
  (let [params (-> request :parameters :params)
        citizen (-> (q/select-citizen {:phone [(:phone params)]})
                    (db/query-first))
        errors (v/validate-create-citizen params)]
    (cond
      (some? citizen)
      (response/conflict (select-keys citizen [:balance :last_name :create_time :email :address :middle_name :second_phone :id :zipcode :first_name :phone :number]))

      (some? errors)
      (response/bad-request (util/->api-errors errors jarima.layout/t))

      :else
      (let [citizen (-> params
                        (assoc :id (random-uuid)
                               :create_time (util/now)
                               :creator_client_id (-> request :stored_client :id))
                        (q/insert-citizen)
                        (db/query-first))]
        (response/ok (select-keys citizen [:balance :last_name :create_time :email :address :middle_name :second_phone :id :zipcode :first_name :phone :number]))))))


(defn citizen-handler
  [request]
  (let [citizen-id (-> request :parameters :path :id)
        citizen (-> (q/select-citizen {:id [citizen-id]})
                    (db/query-first))]
    (if (some? citizen)
      (response/ok (select-keys citizen [:balance :last_name :create_time :email :address :middle_name :second_phone :id :zipcode :first_name :phone :number]))
      (response/not-found {:error             "not-found"
                           :error_description "Гражданин не найден"
                           :errors            {:id ["Не существует"]}}))))

(def routes
  ["/citizens"
   {:middleware []}
   [""
    {:name       :api.citizen/create
     :post       #'create-handler
     :parameters {:params :misc.register/form}}]
   ["/:id"
    {:name       :api.citizen/get
     :get        #'citizen-handler
     :parameters {:path {:id :jarima.spec/uuid}}}]])

