(ns jarima.encoder
  (:import [org.joda.time DateTime])
  (:require
    [jarima.minio :as minio]
    [jarima.ffmpeg :as ffmpeg]
    [clj-http.client :as http]
    [jarima.config :refer [env]]
    [jarima.util :as util]
    [jarima.db.core :as db]
    [clojure.tools.logging :as log]
    [medley.core :refer [random-uuid]]
    [jarima.db.query :as q]
    [mount.core :as mount]
    [clojure.core.async :as async]
    [clj-time.periodic :refer [periodic-seq]]
    [clj-time.core :as time]))


(defn create-task
  [id source target]
  (:body
    (http/post
      (str (-> env :encoder :url) "/task")
      {:as             :json
       :content-type   :json
       :conn-timeout   5000
       :socket-timeout 5000
       :basic-auth     [(-> env :encoder :username) (-> env :encoder :password)]
       :form-params    {:id      id
                        :get_url (minio/get-public-url source)
                        :put_url (minio/get-put-url target)}})))


(defn get-task
  [id]
  (:body
    (http/get
      (str (-> env :encoder :url) "/task/" id)
      {:as         :json
       :basic-auth [(-> env :encoder :username) (-> env :encoder :password)]})))


(defn delete-task
  [id]
  (:body
    (http/delete
      (str (-> env :encoder :url) "/task/" id)
      {:as         :json
       :basic-auth [(-> env :encoder :username) (-> env :encoder :password)]})))



(comment
  (create-task
    #uuid "15fc2e62-0e2e-4884-a131-9f3f0fbbef90"
    "866c74d43200d93e8420bba681138e3b40f679ba.mp4"
    "866c74d43200d93e8420bba681138e3b40f679ba_encoded.mp4")

  (get-task #uuid "15fc2e62-0e2e-4884-a131-9f3f0fbbef90"))


(defn encode-last-videos []
  (->> {:select [:*]
        :from   [:report]
        :where  [:and
                 [:>= :create_time (util/parse-local-date-short "11.04.2021")]
                 [:<= :create_time (util/parse-local-date-short "14.04.2021")]]}
       (db/query)
       (map
         (fn [report]
           #_(log/info (format "Encoding videos for %s..." (:id report)))
           [(when-let [video (:video report)]
              {:task      (create-task (random-uuid) video video)
               :report_id (:id report)})
            (when-let [video (:extra_video report)]
              {:task      (create-task (random-uuid) video video)
               :report_id (:id report)})]))))



(defn encode-user-created-videos [user-id]
  (let [last-reports (-> {:select [:*]
                          :from   [:report]
                          :where  [:and
                                   [:uuid-in :citizen_id [user-id]]
                                   [:str-in :status ["created"]]]}
                         (db/query))]
    (map (fn [report]
           #_(log/info (format "Encoding videos for %s..." (:id report)))
           (when-let [video (:video report)]
             (create-task (random-uuid) video video))
           (when-let [video (:extra_video report)]
             (create-task (random-uuid) video video)))
         last-reports)))


(defn encode-report [report-id]
  (let [report (-> {:select [:*]
                    :from   [:report]
                    :where  [:and
                             [:uuid-in :id [report-id]]
                             #_[:str-in :status ["created"]]]}
                   (db/query-first))]
    #_(log/info (format "Encoding videos for %s..." (:id report)))
    [(when-let [video (:video report)]
       (create-task (random-uuid) video video))
     (when-let [video (:extra_video report)]
       (create-task (random-uuid) video video))]))


(defn encode-report-video [& numbers]
  (let [last-reports (-> {:select [:*]
                          :from   [:report]
                          :where  [:int-in :number numbers]}
                         (db/query))]
    (for [report last-reports]
      (do
        #_(log/info (format "Encoding videos for %s..." (:id report)))
        (when-let [video (:video report)]
          (create-task (random-uuid) video video))
        (when-let [video (:extra_video report)]
          (create-task (random-uuid) video video))))))


(defn extract-last-thumbnails []
  (->> {:select [:*]
        :from   [:report]
        :where  [:and
                 [:!= nil :video]
                 [:= :thumbnail nil]
                 [:and
                  [:>= :create_time (util/parse-local-date-short "13.04.2021")]
                  [:<= :create_time (util/parse-local-date-short "16.04.2021")]]]}
       (db/query)
       (map (fn [{:keys [video id]}]
              (let [thumbnail (-> video
                                  (minio/get-public-url)
                                  (ffmpeg/extract-thumbnail-from-url)
                                  (minio/upload-jpg))]
                (-> id
                    (q/update-report {:thumbnail thumbnail})
                    (db/query-first)))))))

(defn run [_]
  #_(log/info "Running encoder cron")
  (let [now (util/now)]

    ;; Queue not queued reports
    (doseq [report (-> {:select    [:report.id :report.video]
                        :from      [:report]
                        :left-join [:oauth_client [:= :oauth-client.id :report.creator_client_id]]
                        :order-by  [:report.create_time]
                        :limit     100
                        :where     [:and
                                    [:or
                                     [:= nil :oauth-client.id]
                                     [:= true :oauth-client.encoding_required]]
                                    [:= nil :report.review_time]
                                    [:!= nil :report.video]
                                    [:= nil :report.video_encoder_id]]}
                       (db/query))]
      (do
        (log/info (format "Encoding video for %s..." (:id report)))
        (when-let [video (:video report)]
          (when-let [encoder-response (util/mute (create-task (random-uuid) video video))]
            (->> {:video_encoder_id     (:id encoder-response)
                  :video_encoder_status (:status encoder-response)
                  :video_encoder_logs   (when (= (:status encoder-response) "failed")
                                          (:log encoder-response))
                  :video_size           (or (util/mute (minio/get-content-size video)) 0)}
                 (q/update-report (:id report))
                 (db/query))))))

    ;; Queue not queued extra reports
    (doseq [report (-> {:select    [:report.id :report.extra_video]
                        :from      [:report]
                        :left-join [:oauth_client [:= :oauth-client.id :report.creator_client_id]]
                        :order-by  [:report.create_time]
                        :limit     100
                        :where     [:and
                                    [:or
                                     [:= nil :oauth-client.id]
                                     [:= true :oauth-client.encoding_required]]
                                    [:= nil :report.review_time]
                                    [:!= nil :report.extra_video]
                                    [:= nil :report.extra_video_encoder_id]]}
                       (db/query))]
      (do
        (log/info (format "Encoding extra video for %s..." (:id report)))
        (when-let [extra_video (:extra_video report)]
          (when-let [encoder-response (util/mute (create-task (random-uuid) extra_video extra_video))]
            (->> {:extra_video_encoder_id     (:id encoder-response)
                  :extra_video_encoder_status (:status encoder-response)
                  :extra_video_encoder_logs   (when (= (:status encoder-response) "failed")
                                                (:log encoder-response))
                  :extra_video_size           (or (util/mute (minio/get-content-size extra_video)) 0)}
                 (q/update-report (:id report))
                 (db/query))))))


    ;; Update statuses for queued video reports
    (doseq [{:keys [id
                    video_encoder_id
                    video]} (-> {:select   [:id :video_encoder_id :video]
                                 :from     [:report]
                                 :order-by [:report.create_time]
                                 :limit    100
                                 :where    [:and
                                            [:= nil :report.review_time]
                                            [:!= nil :video]
                                            [:!= nil :video_encoder_id]
                                            [:str-in :video_encoder_status ["created" "started"]]]}
                                (db/query))]
      (let [encoder-response (try
                               (get-task video_encoder_id)
                               (catch Exception e
                                 (when (= (:status (ex-data e)) 404)
                                   {:status "not-found"})))]
        (log/info (str "Updating video_encoder statuses") encoder-response)
        (->> (if (= (:status encoder-response) "not-found")
               {:video_encoder_id     nil
                :video_encoder_status nil}
               {:video_encoder_status (or (:status encoder-response) "failed")
                :video_encoder_logs   (when (= (:status encoder-response) "failed")
                                        (:log encoder-response))
                :video_size           (or (util/mute (minio/get-content-size video)) 0)})
             (q/update-report id)
             (db/query))))

    ;; Update statuses for queued extra video reports
    (doseq [{:keys [id
                    extra_video_encoder_id
                    extra_video]} (-> {:select   [:id :extra_video_encoder_id :extra_video]
                                       :from     [:report]
                                       :limit    100
                                       :order-by [:report.create_time]
                                       :where    [:and
                                                  [:= nil :report.review_time]
                                                  [:!= nil :extra_video]
                                                  [:!= nil :extra_video_encoder_id]
                                                  [:str-in :extra_video_encoder_status ["created" "started"]]]}
                                      (db/query))]
      (let [encoder-response (try
                               (get-task extra_video_encoder_id)
                               (catch Exception e
                                 (when (= (:status (ex-data e)) 404)
                                   {:status "not-found"})))]
        (log/info (str "Updating extra_video_encoder statuses") encoder-response)
        (->> (if (= (:status encoder-response) "not-found")
               {:extra_video_encoder_id     nil
                :extra_video_encoder_status nil}
               {:extra_video_encoder_status (or (:status encoder-response) "failed")
                :extra_video_encoder_logs   (when (= (:status encoder-response) "failed")
                                              (:log encoder-response))
                :extra_video_size           (or (util/mute (minio/get-content-size extra_video)) 0)})
             (q/update-report id)
             (db/query)))))
  true)

(comment
  (let [video "temporary-2021/10c952800aa847dca3d587c101b87e65.mp4"]
    (create-task (random-uuid) video video)))

(mount/defstate ticker
  :stop (async/close! ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (periodic-seq (time/seconds 60)))
           {:ch (async/chan (async/sliding-buffer 1))}))


(mount/defstate runner
  :start (async/pipeline-blocking
           1 (async/chan (async/sliding-buffer 1)) (map #'run) ticker))
