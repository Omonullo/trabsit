(ns jarima.handler.api.report
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.util :as util]
    [jarima.handler.citizen.report :as report]
    [jarima.spec :as spec]
    [jarima.minio :as minio]
    [medley.core :refer [assoc-some update-existing]]
    [jarima.reward :refer [reward-type]]
    [ring.util.http-response :as response]
    [conman.core :refer [with-transaction]]
    [jarima.config :refer [dictionary env]]
    [jarima.asbt :refer [offense->short-id]]
    [jarima.ffmpeg :refer [extract-thumbnail]]
    [jarima.layout :refer [t-get t]]
    [jarima.handler.staff.report :refer [video-source]]
    [jarima.validation :refer [validate-create-report validate-update-report validate-video-upload]]
    [jarima.middleware :as middleware]
    [clojure.set :as set]
    [jarima.db.query :as q]
    [jarima.db.core :as db]))


(defn ->client-id [request]
  (-> request :identity :oauth_client :client_id))

(defn ->page [request]
  (or (-> request :parameters :query :page) 1))

(defn ->page-size [request]
  (max (min (or (-> request :parameters :query :size) 15) 100) 5))


(defn report-response [report]
  (-> report
      (update :thumbnail minio/get-public-url)
      (update :video video-source (format "report_%d.mp4" (:number report)))
      (update :extra_video video-source (format "extra_report_%d.mp4" (:number report)))
      (update :extra_video_type (comp :name spec/video-types))
      (assoc :organization
             (when (:organization_id report)
               (-> (q/select-organization {:id [(:organization_id report)]})
                   (db/query-first))))
      (assoc :area
             (when (:area_id report)
               (-> (q/select-area {:id [(:area_id report)]})
                   (db/query-first))))
      (assoc :district
             (when (:district_id report)
               (-> (q/select-district {:id [(:district_id report)]})
                   (db/query-first))))
      (select-keys [:video
                    :organization
                    :area
                    :district
                    :extra_video
                    :extra_video_type
                    :thumbnail
                    :create_time
                    :area_id
                    :lng :lat
                    :district_id
                    :address
                    :locale
                    :incident_time
                    :number
                    :id
                    :citizen_id
                    :status
                    :reward_params
                    :review_time
                    :creator_client_id])))


(defn offense-response [offense]
  (-> offense
      (assoc :short-id (offense->short-id offense))
      (select-keys [:reward_params :citizen_id :reject_time
                    :type_id
                    :number
                    :create_time
                    :vehicle_id
                    :extra_response
                    :fine
                    :forward_time
                    :accept_time
                    :article_id
                    :failure_time
                    :pay_time
                    :dismiss_time
                    :id
                    :extra_img
                    :status
                    :report_id
                    :fine_date
                    :vehicle_id_img
                    :vehicle_img
                    :reward_amount
                    :testimony])
      (update :vehicle_id_img minio/get-public-url)
      (update :vehicle_img minio/get-public-url)
      (update :extra_img minio/get-public-url)
      (assoc :response (-> {:id [(:response_id offense)]}
                           (q/select-response)
                           (db/query-first)))
      (assoc :type
             (when (:type_id offense)
               (-> {:id [(:type_id offense)]}
                   (q/select-offense-type)
                   (db/query-first))))
      (assoc :article (-> {:id [(:article_id offense)]}
                          (q/select-article)
                          (db/query-first)))))



(defn upload-video-handler [request]
  (let [video (report/upload-video (-> request :parameters :params))]
    (if-let [errors (:errors video)]
      (response/bad-request (util/->api-errors errors jarima.layout/t))
      (response/ok video))))


(defn create-handler
  [request]
  (if (:id (:identity request))
    (let [report (report/create (-> request :parameters :params) (:identity request))]
      (if-let [errors (:errors report)]
        (response/bad-request (util/->api-errors errors jarima.layout/t))
        (response/ok (report-response report))))
    (response/bad-request
      {:error             "invalid-request"
       :error_description "Гражданин не найден"
       :errors            {:citizen_id ["Не существует"]}})))


(defn create-form-handler
  [request]
  (let [oauth-client (:oauth_client (:identity request))
        grant-type (:client_grant_type oauth-client)
        scope (set/intersection
                (set (:client_allowed_scope oauth-client))
                (set (:token_scope oauth-client)))]
    (-> (:identity request)
        (report/get-report-form)
        (cond->
          (and (nil? (get scope "read-card-phone")) (not= grant-type "client_credentials"))
          (dissoc :profile)
          (and (nil? (get scope "read-organization")) (not= grant-type "client_credentials"))
          (dissoc :organizations))

        (response/ok))))


(defn report-handler
  [request]
  (let [report-id (-> request :parameters :path :id)
        report (-> {:id                [report-id]
                    :creator_client_id [(->client-id request)]}
                   (assoc-some :citizen_id (:id (:identity request)))
                   (update-existing :citizen_id vector)
                   (q/select-report)
                   (db/query-first))]
    (if (some? report)
      (response/ok (report-response report))
      (response/not-found {:error             "not-found"
                           :error_description "Заявка не найдена"
                           :errors            {:id ["Не существует"]}}))))


(defn fine-report-handler
  [request]
  (let [fine-id (-> request :parameters :path :id)
        report (-> {:creator_client_id [(->client-id request)]}
                   (assoc-some :citizen_id (:id (:identity request)))
                   (update-existing :citizen_id vector)
                   (q/select-report)
                   (assoc
                     :join [:offense [:= :report.id :offense.report_id]]
                     :group-by [:report.id])
                   (q/where-offense {:fine_id [fine-id]})
                   (db/query-first))]
    (if (some? report)
      (response/ok (report-response report))
      (response/not-found {:error             "not-found"
                           :error_description "Заявка не найдена"
                           :errors            {:id ["Не существует"]}}))))


(defn report-list-handler
  [request]
  (->> report-response
       (util/paged-api-query
         #(-> {:creator_client_id [(->client-id request)]}
              (assoc-some :citizen_id (:id (:identity request)))
              (update-existing :citizen_id vector)
              (q/select-report %))
         (->page-size request)
         (->page request))
       (response/ok)))


(defn report-offense-list-handler
  [request]
  (let [report-id (-> request :parameters :path :id)
        report (-> {:id                [report-id]
                    :creator_client_id [(->client-id request)]}
                   (assoc-some :citizen_id (:id (:identity request)))
                   (update-existing :citizen_id vector)
                   (q/select-report)
                   (db/query-first))]
    (if (some? report)
      (->> offense-response
           (util/paged-api-query
             #(-> {:creator_client_id [(->client-id request)]
                   :report_id         [(:id report)]}
                  (q/select-offense %))
             (->page-size request)
             (->page request))
           (response/ok))
      (response/not-found {:error             "not-found"
                           :error_description "Заявка не найдена"
                           :errors            {:id ["Не существует"]}}))))


(defn offense-list-handler
  [request]
  (->> offense-response
       (util/paged-api-query
         #(-> {:creator_client_id [(->client-id request)]}
              (assoc-some :citizen_id (:id (:identity request)))
              (update-existing :citizen_id vector)
              (q/select-offense %))
         (->page-size request)
         (->page request))
       (response/ok)))



(defn offense-handler
  [request]
  (let [offense-id (-> request :parameters :path :id)
        offense (-> {:id                [offense-id]
                     :creator_client_id [(->client-id request)]}
                    (assoc-some :citizen_id (:id (:identity request)))
                    (update-existing :citizen_id vector)
                    (q/select-offense)
                    (db/query-first))]
    (if (some? offense)
      (response/ok (offense-response offense))
      (response/not-found {:error             "not-found"
                           :error_description "Нарушение не найдено"
                           :errors            {:id ["Не существует"]}}))))


(defn fine-offense-handler
  [request]
  (let [fine-id (-> request :parameters :path :id)
        offense (-> {:fine_id           [fine-id]
                     :creator_client_id [(->client-id request)]}
                    (assoc-some :citizen_id (:id (:identity request)))
                    (update-existing :citizen_id vector)
                    (q/select-offense)
                    (db/query-first))]
    (if (some? offense)
      (response/ok (offense-response offense))
      (response/not-found {:error             "not-found"
                           :error_description "Нарушение не найдено"
                           :errors            {:id ["Не существует"]}}))))


(def routes
  [""
   {:middleware [(middleware/wrap-code-scope "send-report")]}
   ["/fine/:id"
    ["/report"
     {:name       :api.fine.report/get
      :get        #'fine-report-handler
      :parameters {:path {:id string?}}}]
    ["/offense"
     {:name       :api.fine.offense/get
      :get        #'fine-offense-handler
      :parameters {:path {:id string?}}}]]
   ["/reports"
    [""
     {:name       :api.report/create
      :post       #'create-handler
      :get        #'report-list-handler
      :parameters {:params :citizen.report/form
                   :query  :api.citizen.list/query}}]
    ["/:id"
     {:name       :api.report/get
      :get        #'report-handler
      :parameters {:path {:id :jarima.spec/uuid}}}]
    ["/:id/offenses"
     {:name       :api.report.offense/list
      :get        #'report-offense-list-handler
      :parameters {:path  {:id :jarima.spec/uuid}
                   :query :api.citizen.list/query}}]]
   ["/offenses"
    [""
     {:name       :api.offense/list
      :get        #'offense-list-handler
      :parameters {:params :citizen.report/form
                   :query  :api.citizen.list/query}}]
    ["/:id"
     {:name       :api.offense/get
      :get        #'offense-handler
      :parameters {:path {:id :jarima.spec/uuid}}}]]
   ["/report"
    ["/video"
     {:name       :api.report/upload-video
      :post       #'upload-video-handler
      :parameters {:params :citizen.report/upload-video}}]
    ["/form"
     {:name :api.report/create-form
      :get  #'create-form-handler}]]])


