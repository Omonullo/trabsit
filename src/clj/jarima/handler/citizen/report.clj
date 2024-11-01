(ns jarima.handler.citizen.report
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.util :as util]
    [jarima.spec :as spec]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [jarima.redis :as redis]
    [jarima.minio :as minio]
    [jarima.redis :as redis]
    [medley.core :refer :all]
    [jarima.encoder :as encoder]
    [jarima.reward :refer [reward-type]]
    [ring.util.http-response :as response]
    [conman.core :refer [with-transaction]]
    [jarima.config :refer [dictionary env]]
    [jarima.asbt :refer [offense->short-id]]
    [jarima.ffmpeg :refer [extract-thumbnail]]
    [jarima.layout :as layout :refer [t-get t]]
    [jarima.handler.staff.report :refer [video-source]]
    [jarima.validation :refer [validate-create-report validate-update-report validate-video-upload]]
    [jarima.middleware :as middleware])
  (:import (java.time LocalDateTime)))


(defn limit-exceeded? [identity]
  (let [offense_count (-> {:select [[:%count.offense.id :offense_count]]
                           :from   [:offense]
                           :join   [:report [:= :offense.report_id :report.id]]
                           :where  [:and
                                    (when (:restricted-area-id env)
                                      [:= :report.area_id (uuid (:restricted-area-id env))])
                                    [:= :report.citizen_id (:id identity)]
                                    [:> :offense.create_time (util/today)]]}
                          (db/query-first :offense_count))]
    (if (nil? (:max_videos_per_day identity))
      (> offense_count (:daily-area-limit env))
      (> offense_count (:max_videos_per_day identity)))))



(defn index
  [{{query :query} :parameters identity :identity}]
  (layout/render
    "citizen/report/list.html"
    (-> (util/paged-query
          (partial (comp #(assoc % :order-by [[:create_time :desc]]) q/select-report)
                   (assoc (util/vectorify-vals query) :citizen_id [(:id identity)]))
          (:page query))
        (assoc :statuses (keys spec/report-statuses))
        (update :paged-rows
          (partial map
                   (fn [report]
                     (-> report
                         (update :thumbnail minio/get-public-url)
                         (assoc :offenses (db/query (q/select-offense {:report_id [(:id report)]})))
                         (assoc :inspector
                                (when (:inspector_id report)
                                  (db/query-first (q/select-staff {:id [(:inspector_id report)]})))))))))))


(defn get-report-form [user]
  {:funds         (->> env :payment :fund (map-vals (fn [fund] (:url fund))))
   :areas         (->> (q/select-area {:obsolete false})
                       (db/query)
                       (map (fn [area]
                              (let [districts (->> (q/select-district {:obsolete false
                                                                       :area_id  [(:id area)]})
                                                   (db/query)
                                                   (map (fn [district]
                                                          (-> district
                                                              (assoc :name (t-get district :name))
                                                              (assoc :yname (t-get district :yname))
                                                              (select-keys [:name :yname :area_id :id])))))]
                                (-> (assoc area :name (t-get area :name)
                                                :yname (t-get area :yname)
                                                :districts districts)
                                    (select-keys [:id :name :districts :yname]))))))
   :articles      (->> (q/select-article {:obsolete false :citizen_selection_enabled true})
                       (db/query))
   :offense-types (->> (q/select-offense-type {})
                       (db/query))
   :profile       (select-keys user (get-in spec/oauth-scopes ["citizen" "read-card-phone" :columns]))
   :reward-types  spec/reward-types
   :limit-exceeded (limit-exceeded? user)
   :organizations (->> {:citizen_id [(:id user)]}
                       (q/select-organization)
                       (db/query)
                       (map #(->> ["citizen" "read-organization" :columns]
                                  (get-in spec/oauth-scopes)
                                  (select-keys %))))})


(defn create-form-handler [req]
  (layout/render "citizen/report/create.html"
    (get-report-form (:identity req))))


(defn ->report
  [identity now {:keys [async video_id video extra_video_id extra_video incident_date incident_time video_encoder_id extra_video_encoder_id] :as params}]
  (merge
    (select-keys params [:reward_params :address :area_id :district_id :lat :lng :extra_video_type])
    {:id                (random-uuid)
     :create_time       now
     :status            "created"
     :citizen_id        (:id identity)
     :creator_client_id (:client_id (:oauth_client identity))
     :creator_client_notified_at (when (some? (:client_id (:oauth_client identity))) now)
     :video_encoder_id video_encoder_id
     :extra_video_encoder_id extra_video_encoder_id
     :video             (if async video_id (minio/upload-video video))
     :extra_video       (if async extra_video_id (minio/upload-video extra_video))
     :thumbnail         (if async (:thumbnail_id (first (redis/get-temp-video video_id)))
                                  (minio/upload-jpg (extract-thumbnail (:tempfile video))))
     :incident_time     (util/local-date-time incident_date incident_time)}))


(defn ->offense
  [identity report now offense-type params]
  (let [is-oauth-client (some? (:client_id (:oauth_client identity)))]
    {:id                         (random-uuid)
     :create_time                now
     :is_enterprise              (or (:enterprise identity) false)
     :status                     "created"
     :creator_citizen_id         (:id identity)
     :type_id                    (:type_id params)
     :creator_client_id          (:client_id (:oauth_client identity))
     :creator_client_notified_at (when (some? (:client_id (:oauth_client identity))) now)
     :report_id                  (:id report)
     :vehicle_id                 (:vehicle_id params)
     :citizen_article_id         (:citizen_article_id params)
     :testimony                  (or (:testimony params) (layout/t-get offense-type :name))}))


(defn ->vehicle
  [params]
  {:id (:vehicle_id params)})


(defn create
  [report user]
  (let [is-oauth-client (some? (:client_id (:oauth_client user)))
        params          (assoc report
                          :async true
                          :incident_date_time (and (:incident_date report) (:incident_time report)
                                                   (LocalDateTime/of
                                                     (:incident_date report)
                                                     (:incident_time report))))]
    (redis/locking-video
      (if-let [errors (-> params
                          ; force offense type validation for non oauth clients
                          (assoc :is_oauth_client is-oauth-client)
                          (update :offenses (fn [offenses]
                                              (map-vals (fn [offense]
                                                          (assoc offense
                                                            :type (when-let [type_id (:type_id offense)]
                                                                    (-> {:id [type_id]}
                                                                        (q/select-offense-type)
                                                                        (db/query-first))))) offenses)))
                          (assoc :is_enterprise (:enterprise user))
                          (validate-create-report))]
        {:errors errors}
        (with-transaction [db/*db*]
          (when (and (:enterprise user)
                     (some
                       (fn [offense]
                         (-> {:select    [[:%count.* :count]]
                              :from      [:offense]
                              :left-join [:report [:= :offense.report_id :report.id]]
                              :where     [:and
                                          [:= :report.incident_time (:incident_date_time params)]
                                          [:= :offense.vehicle_id (:vehicle_id offense)]
                                          [:= :report.citizen_id (:id user)]]}
                             (db/query-first :count)
                             (pos?)))
                       (vals (:offenses params))))
            (response/conflict! {:error "offense-exists"}))
          (let [now    (util/now)
                report (->> params
                            (->report user now)
                            (q/insert-report)
                            (db/query-first))]
            (redis/delete-temp-video (:video_id params))
            (when (:extra_video_id params)
              (redis/delete-temp-video (:extra_video_id params)))
            (db/query (apply q/insert-vehicle (map ->vehicle (-> params :offenses vals))))
            (->> params
                 :offenses
                 vals
                 (map #(->offense user report now
                                  (when (:type_id %)
                                    (-> {:id [(:type_id %)]}
                                        (q/select-offense-type)
                                        (db/query-first)))
                                  %))
                 (apply q/insert-offense)
                 (db/query))
            report))))))


(defn create-handler
  [request]
  (if (limit-exceeded? (:identity request))
    (response/bad-request
      {:errors (-> {:video #{"Превышен дневной лимит загрузки нарушений. (Более 50 нарушений)"}}
                   (util/nest-errors)
                   (util/translate-nested-errors t))})
    (let [report (create (-> request :parameters :params) (:identity request))]
      (if-let [errors (:errors report)]
        (response/bad-request {:errors (-> errors (util/nest-errors) (util/translate-nested-errors t))})
        (-> {:redirect (util/route-path request :citizen.report/view {:id (:id report)})}
            (response/ok))))))


(defn join-rewards [offenses]
  (let [rewards
        (some->> (map :reward_id offenses)
                 (not-empty)
                 (hash-map :id)
                 (q/select-reward)
                 (db/query)
                 (index-by :id))]
    (map #(assoc % :reward (get rewards (:reward_id %)))
         offenses)))


(defn view [request]
  (let [report
        (-> {:id         [(-> request :parameters :path :id)]
             :citizen_id [(-> request :identity :id)]}
            (q/select-report)
            (db/query-first))]
    (when-not report
      (layout/error-page!
        {:status 404
         :title  "Видеозапись не найдена"}))
    (layout/render
      "citizen/report/view.html"
      {:reward_types
       (filter-vals (complement :unavailable) spec/reward-types)

       :organizations
       (-> {:citizen_id [(-> request :identity :id)]}
           (q/select-organization)
           (db/query))

       :funds
       (->> env :payment :fund (map-vals (fn [fund] (:url fund))))

       :report
       (-> report
           (update :thumbnail minio/get-public-url)
           (update :video video-source (format "report_%d.mp4" (:number report)))
           (update :extra_video video-source (format "extra_report_%d.mp4" (:number report)))
           (update :extra_video_type (comp :name spec/video-types))
           (assoc :reward_type (reward-type (:reward_params report)))
           (assoc :inspector
                  (when (:inspector_id report)
                    (-> (q/select-staff {:id [(:inspector_id report)]})
                        (db/query-first))))
           (assoc :offenses
                  (-> (q/select-offense {:report_id [(:id report)]})
                      (update :order-by concat [[:vehicle_id]])
                      (db/query #(assoc % :short-id (offense->short-id %)
                                          :type_name (layout/t-get % :type_name)))
                      (join-rewards)))
           (assoc :organization
                  (when (:organization_id report)
                    (-> (q/select-organization {:id (:organization_id report)})
                        (db/query-first)))))})))


(defn patch [request]
  (let [params (-> request :parameters :params)
        params (if (some? (:card (:reward_params params)))
          (-> params
              (assoc-in [:reward_params :card] true)
              (assoc-in [:card_number] (-> request :identity :card)))
          params)
        report
        (-> {:id         [(-> request :parameters :path :id)]
             :citizen_id [(-> request :identity :id)]}
            (q/select-report)
            (db/query-first))]
    (when-not report
      (layout/error-page!
        {:status 404
         :title  "Видеозапись не найдена"}))
    (if-let [errors (validate-update-report params)]
      (layout/error-page!
        {:status 400
         :title  (t "Плохой запрос")
         :data   (util/pretty-errors errors)})
      (let [reward-params (:reward_params params)]
        (db/query
          (q/update-report
            (:id report)
            {:reward_params reward-params}))
        (db/execute
          {:update    :reward
           :returning [:*]
           :set       {:type   (reward-type reward-params)
                       :params (db/raw-value reward-params)
                       :failure_message nil
                       :transaction_number nil}
           :where     [:and
                       [:!= "paid" :status]
                       [:in :id (-> {:report_id [(:id report)]}
                                    (q/select-offense)
                                    (db/query :reward_id))]]})
        (-> (util/route-path request :citizen.report/view {:id (:id report)})
            (response/found))))))


(defn upload-video [params]
  (if-let [errors (validate-video-upload params)]
    {:errors errors}
    (let [id (minio/upload-video (:video params))]
      (redis/add-temp-video id {:id           id
                                :create_time  (util/now)
                                :thumbnail_id (-> (:tempfile (:video params))
                                                  (extract-thumbnail)
                                                  (minio/upload-jpg))})
      (assoc (video-source id "video.mp4") :id id))))


(defn upload-video-handler [request]
  (let [video (upload-video (-> request :parameters :params))]
    (if-let [errors (:errors video)]
      (response/bad-request {:errors errors})
      (response/ok video))))


(def routes
  ["/reports"
   [""
    {:name       :citizen.report/index
     :get        #'index
     :parameters {:query :citizen.report.index/query}}]

   ["/new"
    {:name :citizen.report/create
     :middleware [middleware/maintenance-middleware
                  middleware/wrap-citizen-upload-permission]
     :get  #'create-form-handler}]

   ["/:id/view"
    {:name       :citizen.report/view
     :get        #'view
     :parameters {:path {:id :jarima.spec/uuid}}}]

   ["/:id/patch"
    {:name       :citizen.report/patch
     :post       #'patch
     :parameters {:path   {:id :jarima.spec/uuid}
                  :params :citizen.report/form}}]])


(def api-routes
  [""
   {:middleware [middleware/maintenance-middleware
                 middleware/wrap-citizen-upload-permission]}
   ["/reports"
    {:name       :citizen.report.api/create
     :post       #'create-handler
     :parameters {:params :citizen.report/form}}]
   ["/report/video"
    {:name       :citizen.report.api/upload-video
     :post       #'upload-video-handler
     :parameters {:params {:video :entity/video}}}]])
