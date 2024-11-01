(ns jarima.detector
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
    [clj-time.core :as time]
    [cheshire.core :as json]))


(defn create-task
  [source target]
  (:body
    (http/post
      (str (-> env :detector :url) "/tasks")
      {:as             :json
       :content-type   :json
       :conn-timeout   5000
       :socket-timeout 5000
       :basic-auth     [(-> env :detector :username) (-> env :detector :password)]
       :form-params    {:get_url (minio/get-public-url source)
                        :put_url (minio/get-put-url target)}})))


(defn get-task
  [id]
  (:body
    (http/get
      (str (-> env :detector :url) "/tasks/" id)
      {:as         :json
       :basic-auth [(-> env :detector :username) (-> env :detector :password)]})))



(comment
  (create-task
    "staging/17a389dd453542d69959e38fadde82c5.mp4"
    "staging/17a389dd453542d69959e38fadde82c5_detected.mp4"))


(comment (get-task 34))


(defn run [_]
  (log/info "Running detector cron")
  (let [now (util/now)]

    ;; Queue not queued reports
    (doseq [report (-> {:select    [:report.id :report.video]
                        :from      [:report]
                        :left-join [:oauth_client [:= :oauth-client.id :report.creator_client_id]]
                        :order-by  [:report.create_time]
                        :limit     100
                        :where     [:and
                                    [:!= nil :report.video]
                                    [:= nil :report.video_detector_id]
                                    [:or
                                     [:= true :report.video_force_detect]
                                     [:and
                                      [:= nil :report.review_time]
                                      [:or
                                       [:= :report.video_encoder_status "finished"]
                                       [:= false :oauth-client.encoding_required]]]]]}

                       (db/query))]
      (do
        (log/info (format "Detecting video for %s..." (:id report)))
        (when-let [video (:video report)]
          (when-let [detector-response (try
                                         (create-task video (util/replace-ext video "detections" "json"))
                                         (catch Exception e
                                                (log/error "Error in detection" e)))]
            (->> {:video_detector_id     (:id detector-response)
                  :video_detector_status (:status detector-response)}
                 (q/update-report (:id report))
                 (db/query))))))

    ;; Queue not queued extra reports
    (doseq [report (-> {:select    [:report.id :report.extra_video]
                        :from      [:report]
                        :left-join [:oauth_client [:= :oauth-client.id :report.creator_client_id]]
                        :order-by  [:report.create_time]
                        :limit     100
                        :where     [:and
                                    [:!= nil :report.extra_video]
                                    [:= nil :report.extra_video_detector_id]
                                    [:or
                                     [:= true :report.extra_video_force_detect]
                                     [:and
                                      [:= nil :report.review_time]
                                      [:or
                                       [:= :extra_video_encoder_status "finished"]
                                       [:= false :oauth-client.encoding_required]]]]]}

                       (db/query))]
      (do
        (log/info (format "Detecting extra video for %s..." (:id report)))
        (when-let [extra_video (:extra_video report)]
          (when-let [detector-response (try
                                         (create-task extra_video (util/replace-ext extra_video "detections" "json"))
                                         (catch Exception e
                                           (log/error "Error in detection" e)))]
            (->> {:extra_video_detector_id     (:id detector-response)
                  :extra_video_detector_status (:status detector-response)}
                 (q/update-report (:id report))
                 (db/query))))))


    ;; Update statuses for queued video reports
    (doall
      (pmap
        (fn [{:keys [id video_detector_id]}]
          (when-let [detector-response (util/mute (get-task video_detector_id))]
            (->> {:video_detector_id     (:id detector-response)
                  :video_detector_status (:status detector-response)
                  :video_detector_logs   (when (= (:status detector-response) "failed")
                                           (json/encode (:result detector-response)))}
                 (q/update-report id)
                 (db/query))))
        (-> {:select   [:id :video_detector_id]
             :from     [:report]
             :order-by [:report.create_time]
             :limit     100
             :where    [:and
                        [:!= nil :video_detector_id]
                        [:= nil :report.review_time]
                        [:!= nil :report.video]
                        [:str-in :video_detector_status ["created"]]]}
            (db/query))))


    ;; Update statuses for queued extra video reports
    (doall
      (pmap
        (fn [{:keys [id extra_video_detector_id]}]
          (when-let [detector-response (util/mute (get-task extra_video_detector_id))]
            (->> {:extra_video_detector_id     (:id detector-response)
                  :extra_video_detector_status (:status detector-response)
                  :extra_video_detector_logs   (when (= (:status detector-response) "failed")
                                                 (json/encode (:result detector-response)))}
                 (q/update-report id)
                 (db/query))))
        (-> {:select   [:id :extra_video_detector_id]
             :from     [:report]
             :order-by [:report.create_time]
             :limit     100
             :where    [:and
                        [:!= nil :extra_video]
                        [:= nil :report.review_time]
                        [:!= nil :extra_video_detector_id]
                        [:str-in :extra_video_detector_status ["created"]]]}
            (db/query)))))

  true)


(mount/defstate ticker
  :stop (async/close! ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (periodic-seq (time/seconds 100)))
           {:ch (async/chan (async/sliding-buffer 1))}))


(mount/defstate runner
  :start (async/pipeline-blocking
           1 (async/chan (async/sliding-buffer 1)) (map #'run) ticker))
