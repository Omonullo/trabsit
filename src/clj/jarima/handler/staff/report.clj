(ns jarima.handler.staff.report
  (:require
    [jarima.util :as util]
    [jarima.spec :as spec]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [jarima.minio :as minio]
    [jarima.layout :as layout]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [cheshire.core :refer [generate-string]]
    [jarima.ffmpeg :refer [extract-thumbnail]]
    [jarima.asbt :refer [offense->short-id]]
    [medley.core :refer [uuid random-uuid map-vals filter-vals index-by distinct-by]]
    [jarima.middleware :as middleware]
    [jarima.sms :as sms]
    [jarima.util :as util]
    [jarima.config :refer [mwage]]
    [selmer.parser :refer [render-file]]
    [conman.core :refer [with-transaction]]
    [medley.core :refer [uuid random-uuid map-vals filter-vals]]
    [jarima.validation :as v]
    [cheshire.core :as json]))


(defn video-source
  [video filename]
  (when (seq video)
    (util/mute
      {:download-url (minio/get-download-url video filename)
       :url          (minio/get-public-url video)
       :content-type (minio/get-content-type video)})))


(defn file-source
  [file-id]
  (when (seq file-id)
    (util/mute
      (minio/get-public-url file-id))))


(defn citizen [{{query :query} :parameters}]
  (-> query
      (q/select-citizen {:size 30 :page 1})
      (db/query #(-> (select-keys % [:id :phone :first_name :last_name :middle_name])
                     (update :phone util/format-phone)))
      (response/ok)))


(defn staff [{{query :query} :parameters}]
  (-> query
      (q/select-staff {:size 30 :page 1})
      (db/query #(-> (select-keys % [:id :phone :first_name :last_name :middle_name])
                     (update :phone util/format-phone)))
      (response/ok)))


(defn restrict-filter
  [filter identity]
  (if (= (:role identity) "inspector")
    (cond-> filter
      (:area_id identity) (assoc :area_id [(:area_id identity)])
      (:district_id identity) (assoc :district_id [(:district_id identity)])
      (and (:area_id identity) (not (:district_id identity)))
      (update :district_id (set (db/query (q/select-district {:area_id [(:area_id identity)]}) :id))))
    filter))


(defn before-index
  [request]
  (update-in
    request [:parameters :query :sort]
    (fn [v]
      (or v (if (= "admin" (-> request :identity :role))
              "create_time_desc"
              "create_time_asc")))))


(defn index
  [{{query :query} :parameters identity :identity :as request}]
  (layout/render
    "staff/report/list.html"
    (-> (util/paged-query
          (partial q/select-report
                   (-> (restrict-filter query identity)
                       (util/vectorify-vals (complement #{:create_time :sort :point_radius}))))
          (:page query))
        (assoc :request request)
        (assoc :statuses (keys spec/report-statuses))
        (assoc :citizens (-> {:id [(:citizen_id query)]}
                             (q/select-citizen {:size 1 :page 1})
                             (db/query)))
        (update :paged-rows
                (partial map
                         (fn [report]
                           (-> report
                               (update :thumbnail minio/get-public-url)
                               (assoc :offenses (db/query (q/select-offense {:report_id [(:id report)]})))
                               (assoc :citizen (first (db/query (q/select-citizen {:id [(:citizen_id report)]})))))))))))


(defn view [request]
  (let [report
        (-> {:id (-> request :parameters :path :id)}
            (restrict-filter (:identity request))
            (util/vectorify-vals)
            (q/select-report)
            (db/query)
            (first))
        inspectors
        (->> (q/select-staff {})
             (db/query)
             (index-by :id))
        revision
        (when-let [version (-> request :parameters :query :version)]
          (-> {:version   [version]
               :report_id [(:id report)]}
              (q/select-revision)
              (db/query-first #(update % :data read-string))))]
    (when-not report
      (layout/error-page!
        {:status 404
         :title  "Видеозапись не найдена"}))
    (when (and (-> request :parameters :query :version) (not revision))
      (layout/error-page!
        {:status 404
         :title  "Ревизия не найдена"}))
    (let [final-report (if revision (:data revision) report)
          query-params (:query-params request)]
      (layout/render
        "staff/report/view.html"
        {:next-url (if (get query-params "next_url")
                     (get query-params "next_url")
                     (str (:uri request) "?" (:query-string request)))
         :revision revision
         :report   (-> final-report
                       (update :video video-source (format "report_%d.mp4" (:number report)))
                       (update :extra_video video-source (format "extra_report_%d.mp4" (:number report)))
                       (update :extra_video_type (comp :name spec/video-types))
                       (update :thumbnail minio/get-public-url)
                       (assoc :inspector (inspectors (:inspector_id report)))
                       (assoc :nearby_count "Не доступно")
                       (assoc :offenses
                         (map
                           #(-> %
                                (assoc :short-id (offense->short-id %))
                                (assoc
                                  :type_name (layout/t-get % :type_name)
                                  :creator_citizen (-> {:id [(:creator_citizen_id %)]}
                                                       (q/select-citizen)
                                                       (db/query-first))
                                  :creator_staff (-> {:id [(:creator_staff_id %)]}
                                                     (q/select-staff)
                                                     (db/query-first)))
                                (update :vehicle_img minio/get-public-url)
                                (update :vehicle_id_img minio/get-public-url)
                                (update :extra_img minio/get-public-url))
                           (if revision
                             (-> revision :data :offenses)
                             (-> (q/select-offense {:report_id [(:id report)]})
                                 (assoc :order-by [:offense.number])
                                 (db/query)))))
                       (assoc :offense-rewards
                         (-> (q/select-offense-reward (:id report))
                             (db/query :params)
                             (distinct)
                             (seq)))
                       (assoc :revisions
                         (-> {:report_id [(:id report)]}
                             (q/select-revision)
                             (db/query #(let [revision (update % :data read-string)]
                                          (->> (inspectors (:inspector_id (:data revision)))
                                               (assoc-in revision [:data :inspector]))))))
                       (assoc :citizen
                         (-> {:select [:*]
                              :from   [:citizen]
                              :where  [:= :id (:citizen_id report)]}
                             (db/query)
                             (first)
                             (assoc :stat
                               {:report-count  (-> {:select [[:%count.* :count]]
                                                    :from   [:report]
                                                    :where  [:= :citizen_id (:citizen_id report)]}
                                                   (db/query :count)
                                                   (first))
                                :offense-count (-> {:select    [[:%count.* :count]]
                                                    :from      [:offense]
                                                    :left-join [:report [:= :report.id :offense.report_id]]
                                                    :where     [:= :citizen_id (:citizen_id report)]}
                                                   (db/query :count)
                                                   (first))})))
                       (assoc :organization
                              (when (:organization_id report)
                                (-> {:select [:*]
                                     :from   [:organization]
                                     :where  [:= :id (:organization_id report)]}
                                    (db/query)
                                    (first)))))}))))

(defn encode-video-report [video_type request]
  (let [updated-report (-> (-> request :parameters :path :id)
                           (q/update-report {(keyword (str video_type "_encoder_id"))     nil
                                             (keyword (str video_type "_encoder_status")) nil})
                           (db/query-first))]
    (if updated-report
      (response/ok {:status "ok"})
      (response/not-found {:status "not found"}))))


(defn detect-video-report [video_type request]
  (let [updated-report (-> (-> request :parameters :path :id)
                           (q/update-report
                             {(keyword (str video_type "_force_detect"))    true
                              (keyword (str video_type "_detector_id"))     nil
                              (keyword (str video_type "_detector_status")) nil})
                           (db/query-first))]
    (if updated-report
      (response/ok {:status "ok"})
      (response/not-found {:status "not found"}))))


(defn vehicle [{{query :query} :parameters}]
  (let [count (-> {:select    [[:%count.* :count]]
                   :from      [:offense]
                   :left-join [:report [:= :offense.report_id :report.id]]
                   :where     [:and
                               (when (:incident_time query)
                                 [:within :report.incident_time (:incident_time query)])
                               [:= :vehicle_id (:vehicle_id query)]
                               [:!= :report_id (:exclude_report_id query)]]}
                  (db/query-first :count))]
    (response/ok
      {:count count})))


(defn get-review-form [identity report]
  (let [offenses
        (-> {:report_id [(:id report)]}
            (q/select-offense)
            (assoc :order-by [:offense.number])
            (db/query
              #(-> %
                   (assoc :short-id (offense->short-id %))
                   (assoc
                     :creator_citizen (-> {:id [(:creator_citizen_id %)]}
                                          (q/select-citizen)
                                          (db/query-first))
                     :creator_staff (-> {:id [(:creator_staff_id %)]}
                                        (q/select-staff)
                                        (db/query-first)))
                   (update :vehicle_img minio/get-public-url)
                   (update :vehicle_id_img minio/get-public-url)
                   (update :extra_img minio/get-public-url))))
        vehicle-offense-count
        (->> {:select   [:vehicle_id [:%count.* :count]]
              :from     [:offense]
              :group-by [:vehicle_id]
              :where    [:and
                         [:in :vehicle_id (map :vehicle_id offenses)]
                         [:!= :report_id (:id report)]]}
             (db/query)
             (map (juxt :vehicle_id :count))
             (into {}))]
    {:areas         (->> (q/select-area {:obsolete false})
                         (db/query)
                         (map (fn [area]
                                (let [districts (->> (q/select-district {:obsolete false
                                                                         :area_id  [(:id area)]})
                                                     (db/query)
                                                     (map (fn [district]
                                                            (-> district
                                                                (assoc :name (layout/t-get district :name))
                                                                (assoc :yname (layout/t-get district :yname))
                                                                (select-keys [:name :yname :area_id :id])))))]
                                  (-> (assoc area :name (layout/t-get area :name)
                                          :yname (layout/t-get area :yname)
                                          :districts districts)
                                      (select-keys [:id :name :districts :yname]))))))
     :responses (-> {:select   [:*]
                     :from     [:response]
                     :order-by [:priority]
                     :where    [:!= :obsolete true]}
                    (db/query))
     :articles  (-> {:select   [:*]
                     :from     [:article]
                     :where    [:not :obsolete]
                     :order-by [:factor :number]}
                    (db/query))
     :report    (-> report
                    (assoc :nearby_count "Не доступно")
                    (assoc :creator_client (-> {:id      [(:creator_client_id report)]}
                                               (q/select-oauth-client)
                                               (db/query-first)
                                               (select-keys [:id :name :url :logo :enabled :encoding_required])
                                               (not-empty)))
                    (update :video video-source (format "report_%d.mp4" (:number report)))
                    (update :extra_video video-source (format "extra_report_%d.mp4" (:number report)))
                    (update :extra_video_type (comp :name spec/video-types))
                    (assoc :video_detections (when (= (:video_detector_status report) "succeeded")
                                               (file-source (util/replace-ext (:video report) "detections" "json"))))
                    (assoc :extra_video_detections (when (= (:extra_video_detector_status report) "succeeded")
                                                     (file-source (util/replace-ext (:extra_video report) "detections" "json"))))
                    (update :thumbnail minio/get-public-url)
                    (assoc :offenses (->> offenses
                                          (map
                                            (fn [offense]
                                              (-> offense
                                                  (assoc :vehicle-offense-count
                                                    (-> offense
                                                        (:vehicle_id)
                                                        (vehicle-offense-count)
                                                        (or 0))))))
                                          (concat [nil])))
                    (assoc :citizen
                      (merge (when (= (:role identity) "admin")
                               (-> {:select [:*]
                                    :from   [:citizen]
                                    :where  [:= :id (:citizen_id report)]}
                                   (db/query)
                                   (first)))
                             {:id   (:citizen_id report)
                              :stat {:report-count  (-> {:select [[:%count.* :count]]
                                                         :from   [:report]
                                                         :where  [:= :citizen_id (:citizen_id report)]}
                                                        (db/query :count)
                                                        (first))
                                     :offense-count (-> {:select    [[:%count.* :count]]
                                                         :from      [:offense]
                                                         :left-join [:report [:= :report.id :offense.report_id]]
                                                         :where     [:= :citizen_id (:citizen_id report)]}
                                                        (db/query :count)
                                                        (first))}}))
                    (assoc :inspector
                      (-> {:select [:*]
                           :from   [:citizen]
                           :where  [:= :id (:inspector_id report)]}
                          (db/query)
                          (first)))
                    (assoc :organization
                      (when (:organization_id report)
                        (-> {:select [:*]
                             :from   [:organization]
                             :where  [:= :id (:organization_id report)]}
                            (db/query)
                            (first)))))}))


(defn render-review-form
  [request]
  (conman.core/with-transaction [db/*db*]
    (let [report (-> {:id (-> request :parameters :path :id)}
                     (restrict-filter (:identity request))
                     (util/vectorify-vals)
                     (q/select-report)
                     (db/query-first))]
      (when-not report
        (layout/error-page!
          {:status 404
           :title  "Видеозапись не найдена"}))
      (when (and (= "reviewed" (:status report)) (not (-> request :params :force)))
        (-> (util/route-path request :staff.report/view {:id (-> request :parameters :path :id)})
            (response/found)
            (util/recall-referer request (:base-url env))
            (response/throw!)))
      (util/remember-referer
        (layout/render
          "staff/report/review.html"
          (get-review-form (:identity request) report))
        request
        (:base-url env)))))


(defn render-review2-form
  [request]
  (layout/render "staff/report/review2.html"
    {:review_front_url (:review-url env)
     :id               (-> request :parameters :path :id)}))


(defn api-review-form
  [request]
  (conman.core/with-transaction [db/*db*]
    (let [report (-> {:id (-> request :parameters :path :id)}
                     (restrict-filter (:identity request))
                     (util/vectorify-vals)
                     (q/select-report)
                     (db/query-first))]
      (when-not report
        (response/not-found!
          {:status 404
           :title  "Видеозапись не найдена"}))
      (when (and (= "reviewed" (:status report)) (not (-> request :params :force)))
        (response/bad-request!
          {:status 400
           :title  "Видеозапись уже рассмотрена"}))
      (-> (:identity request)
          (get-review-form report)
          (response/ok)
          (util/remember-referer request (:base-url env))))))


(defn persist-vehicles
  "For a given list of offense form params
  inserts vehicle id rows into vehicles db.
  Returns list of offense form params as is."
  [offenses]
  (let [vehicle_ids (some->>
                      (seq offenses)
                      (map :vehicle_id)
                      (filter identity))]
    (when (seq vehicle_ids)
      (->> vehicle_ids
           (map #(hash-map :id %))
           (apply q/insert-vehicle)
           (db/query))))
  offenses)


(defmulti ->offense
          "For a given report and a offense for params
          returns a map of columns to be persisted to offenses
          db."
          (fn [_report _identity _now params]
            (:status params)))


(defmethod ->offense "rejected"
  [_report _identity now params]
  {:id              (:id params)
   :vehicle_id      (:vehicle_id params)

   :status          "rejected"

   :reject_time     now
   :response_id     (:response_id params)
   :extra_response  (:extra_response params)

   :accept_time     nil
   :article_id      nil
   :vehicle_id_img  nil
   :vehicle_img     nil
   :extra_img       nil
   :fine            nil

   :failure_time    nil
   :failure_message nil})


(defmethod ->offense "accepted"
  [report identity now params]
  (let [article (-> {:id [(:article_id params)]}
                    (q/select-article)
                    (db/query-first))]
    (cond->
      {:id              (:id params)
       :vehicle_id      (:vehicle_id params)

       :status          "accepted"

       :accept_time     now
       :article_id      (:id article)

       :reject_time     nil
       :response_id     nil
       :extra_response  nil

       :failure_time    nil
       :failure_message nil}

      (:vehicle_img params)
      (assoc :vehicle_img (minio/upload-jpg (:vehicle_img params)))

      (:vehicle_id_img params)
      (assoc :vehicle_id_img (minio/upload-jpg (:vehicle_id_img params)))

      (:extra_img params)
      (assoc :extra_img (minio/upload-jpg (:extra_img params)))

      (not (:id params))
      (merge {:id               (random-uuid)
              :creator_staff_id (:id identity)
              :create_time      now
              :report_id        (:id report)}))))


(defn persist-offense
  "For a given offense, updates or creates offense
  db rows with actual information. Returns list of
  updated or created offenses."
  [offense]
  (db/query-first
    (if (:create_time offense)
      (q/insert-offense offense)
      (q/update-offense (:id offense) (dissoc offense :vehicle_id)))))


(defn forget-offenses
  "For a given report and a list of submitted offenses
  removes from offenses db every offense which was
  created by inspector and not forwarded and not listed
  in submitted offenses. Returns list of deletion
  result as boolean."
  [report submitted-offenses]
  (->> {:select [:id]
        :from   [:offense]
        :where  [:and
                 [:= :testimony nil]
                 [:= :forward_time nil]
                 [:= :report_id (:id report)]]}
       (db/query)
       (remove (comp (set (map :id submitted-offenses)) :id))
       (mapv #(db/execute {:delete-from :offense :where [:= :id (:id %)]}))))


(defn take-snapshot
  [report now]
  (db/query
    (q/insert-revision
      {:invalidate_time now
       :report_id       (:id report)
       :data            (->> {:select   [:*]
                              :from     [:offense]
                              :where    [:= :report_id (:id report)]
                              :order-by [:vehicle_id]}
                             (q/for-update)
                             (db/query)
                             (assoc report :offenses)
                             (prn-str))})))


(defn ignore-forwarded
  "For a given report and a list of submitted offenses
  removes from latter every offense which was already
  forwarded to asbt."
  [report submitted-offenses]
  (-> {:select [:id]
       :from   [:offense]
       :where  [:and [:!= :forward_time nil] [:= :report_id (:id report)]]}
      (db/query :id)
      (set)
      (comp :id)
      (remove submitted-offenses)))


(defn ignore-accepted
  "For a given report and a list of submitted offenses
  removes from latter every offense which was already
  forwarded to asbt."
  [report submitted-offenses]
  (-> {:select [:id]
       :from   [:offense]
       :where  [:and
                [:= :report_id (:id report)]
                [:or
                 ; ignore forwarded
                 [:!= :forward_time nil]
                 ; ignore accepted and not failed or queued again
                 [:and
                  [:!= :accept_time nil]
                  [:or
                   [:= :failure_time nil]
                   [:= :failure_message nil]]]]]}
      (db/query :id)
      (set)
      (comp :id)
      (remove submitted-offenses)))


(defn persist-report
  "For a given report, an inspector identity and
  report for params, updates report db with actual
  information. Returns updated report."
  [report identity now params]
  (db/query-first
    (q/update-report
      (:id report)
      {:status       "reviewed"
       :review_time  now
       :inspector_id (:id identity)
       :district_id  (:district_id params)
       :address      (:address params)})))


(defn notify
  "For a given report sends sms message
  notifying that report has been reviewed.
  Return true on success."
  [report]
  (let [citizen (db/query-first (q/select-citizen {:id [(:citizen_id report)]}))]
    (when (nil? (:creator_client_id report))
      (sms/send
        (:phone citizen)
        (binding [layout/*request* {:locale (:locale citizen)}]
          (render-file "sms/reviewed.txt" {:report report :domain (util/host (:base-url env))}))))))


(defn review
  [request]
  (let [report (-> {:id (-> request :parameters :path :id)}
                   (restrict-filter (:identity request))
                   (util/vectorify-vals)
                   (q/select-report)
                   (db/query)
                   (first))
        now (util/now)]
    (when-not report
      (layout/error-page!
        {:status 404
         :title  "Видеозапись не найдена"}))
    (when-let [errors (v/validate-review-report
                        (get-in request [:parameters :params]))]
      (layout/error-page!
        {:status 400
         :title  "Ошибка"
         :data   (json/encode (-> errors (util/nest-errors) (util/translate-nested-errors layout/t)))}))
    (with-transaction [db/*db*]
      (take-snapshot report now)
      (->> (get-in request [:parameters :params :offenses])
           (vals)
           (ignore-forwarded report)
           (persist-vehicles)
           (map (partial ->offense report (:identity request) now))
           (map persist-offense)
           (forget-offenses report))
      (->> (get-in request [:parameters :params])
           (persist-report report (:identity request) now)))
    (future (notify report))
    (-> (util/route-path request :staff.report/index)
        (response/found)
        (util/recall-referer request (:base-url env)))))


(defn api-review
  [request]
  (let [report (-> {:id (-> request :parameters :path :id)}
                   (restrict-filter (:identity request))
                   (util/vectorify-vals)
                   (q/select-report)
                   (db/query)
                   (first))
        now (util/now)]
    (when-not report
      (response/not-found! {:status "not found"}))
    (when-let [errors (v/validate-review-report
                        (get-in request [:parameters :params]))]
      (response/bad-request! {:status "validation_error"
                              :errors (-> errors (util/nest-errors) (util/translate-nested-errors layout/t))}))
    (with-transaction [db/*db*]
      (take-snapshot report now)
      (->> (get-in request [:parameters :params :offenses])
           (vals)
           (ignore-accepted report)
           (persist-vehicles)
           (map (partial ->offense report (:identity request) now))
           (map persist-offense)
           (doall))
      (->> (get-in request [:parameters :params])
           (persist-report report (:identity request) now)))
    (future (notify report))
    (-> {:status "ok"}
        (response/ok)
        (util/recall-referer request (:base-url env)))))


(def api-routes
  ["/reports"
   ["/:id/video/encode"
    {:name       :api.staff.report.video/encode
     :post       (partial encode-video-report "video")
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["/:id/extra_video/encode"
    {:name       :api.staff.report.extra-video/encode
     :post       (partial encode-video-report "extra_video")
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["/:id/video/detect"
    {:name       :api.staff.report.video/detect
     :post       (partial detect-video-report "video")
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["/:id/extra_video/detect"
    {:name       :api.staff.report.extra-video/detect
     :post       (partial detect-video-report "extra_video")
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["/:id/review"
    {:middleware [middleware/wrap-review-area]
     :name       :api.staff.report/review
     :get        #'api-review-form
     :post       #'api-review
     :parameters {:path   {:id :jarima.spec/uuid}
                  :params :staff.report/form}}]])


(def routes
  ["/reports"
   [""
    {:name       :staff.report/index
     :get        (comp #'index #'before-index)
     :parameters {:query :staff.report.index/query}}]
   ["/citizen"
    {:name       :staff.report/citizen
     :get        #'citizen
     :parameters {:query :staff.report.citizen/query}}]
   ["/staff"
    {:name       :staff.report/staff
     :get        #'staff
     :parameters {:query :staff.report.citizen/query}}]
   ["/:id/view"
    {:name       :staff.report/view
     :get        #'view
     :parameters {:path  {:id :jarima.spec/uuid}
                  :query :staff.report.view/query}}]
   ["/:id/review"
    {:middleware [middleware/wrap-review-area]
     :name       :staff.report/review
     :get        #'render-review-form
     :post       #'review
     :parameters {:path   {:id :jarima.spec/uuid}
                  :params :staff.report/form}}]
   ["/:id/review2"
    {:middleware [middleware/wrap-review-area]
     :name       :staff.report/review2
     :get        #'render-review2-form
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["/vehicle"
    {:name       :staff.report/vehicle
     :get        #'vehicle
     :parameters {:query :inspector.report.vehicle/query}}]])
