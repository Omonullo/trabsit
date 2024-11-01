(ns jarima.minio
  (:import [java.util HashMap UUID]
           [org.joda.time DateTime]
           [io.minio MinioClient ErrorCode])
  (:require [chime]
            [buddy.core.hash]
            [buddy.core.codecs]
            [jarima.util :as util]
            [clojure.java.io :as io]
            [jarima.redis :as redis]
            [clj-time.core :as time]
            [jarima.config :refer [env]]
            [mount.core :refer [defstate]]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [medley.core :refer [random-uuid]]
            [clj-time.periodic :refer [periodic-seq]]
            [jarima.db.core :as db]
            [jarima.db.query :as query]
            [clojure.string :as str]))


(defstate clients
  :start (->> (:minio-servers env)
              (mapcat
                (fn [[bucket-name config]]
                  (let [bucket-name (name bucket-name)
                        client      (new MinioClient
                                         (get-in config [:private-endpoint])
                                         (get-in config [:access-key])
                                         (get-in config [:secret-key])
                                         (get-in config [:private-secure]))]
                    (when (and (not (.bucketExists client bucket-name))
                               (:dev env))
                      (.makeBucket client bucket-name))
                    (when-not (:disabled config)
                      [[(keyword bucket-name) client]]))))
              (into {})))



(defstate public-clients
  :start (->> (:minio-servers env)
              (mapcat
                (fn [[bucket-name config]]
                  (let [bucket-name (name bucket-name)
                        client      (new MinioClient
                                         (get-in config [:public-endpoint])
                                         (get-in config [:access-key])
                                         (get-in config [:secret-key])
                                         (get-in config [:public-secure]))]
                    (when (and (not (.bucketExists client bucket-name))
                               (:dev env))
                      (.makeBucket client bucket-name))
                    (when-not (:disabled config)
                      [[(keyword bucket-name) client]]))))
              (into {})))


(defn list-objects
  [bucket-name]
  (->> (.listObjects (get clients (keyword bucket-name)) (name bucket-name))
       (map #(-> % .get .objectName))))

(defn obj->obj-id-bucket-name
  "Return [obj-id, bucket-name]"
  [obj]
  (let [[bucket-name obj-id] (take-last 2 (str/split obj #"/"))]
    (assert (not-empty obj-id) "Minio obj-id is null!")
    (assert (not-empty bucket-name) "Minio bucket-name is null!")
    [obj-id bucket-name]))


(defn remove-object
  [obj]
  (log/info "Deleting minio object" obj)
  (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
    (.removeObject (get clients (keyword bucket-name))
                   bucket-name
                   obj-id)))


(defn upload-video
  [{:keys [tempfile size]}]
  (when tempfile
    (let [obj (str
                (:minio-upload-bucket env) "/"
                (util/uuid->guid (random-uuid)) ".mp4")]
      (with-open [stream (io/input-stream tempfile)]
        (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
          (.putObject
            (get clients (keyword bucket-name))
            (name bucket-name)
            obj-id
            stream size
            (new HashMap {"Content-Type" "video/mp4"}))))
      obj)))


(defn upload-jpg
  [bytes]
  (let [obj (str
              (:minio-upload-bucket env) "/"
              (random-uuid) ".jpg")]
    (with-open [stream (io/input-stream bytes)]
      (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
        (.putObject
          (get clients (keyword bucket-name))
          (name bucket-name)
          obj-id
          stream (long (count bytes))
          (new HashMap
               {"Content-Type" "image/jpg"}))))
    obj))


(defn upload-jpg-with-id
  [bytes obj-id]
  (let [obj obj-id]
    (with-open [stream (io/input-stream bytes)]
      (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
        (.putObject
          (get clients (keyword bucket-name))
          (name bucket-name)
          obj-id
          stream (long (count bytes))
          (new HashMap
               {"Content-Type" "image/jpg"}))))
    obj))


(defn get-public-url
  [obj]
  (when obj
    (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
      (when obj-id
        (util/mute (.presignedGetObject
                     (get public-clients (keyword bucket-name))
                     (name bucket-name)
                     obj-id))))))


(defn get-put-url
  [obj]
  (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
    (.presignedPutObject
      (get public-clients (keyword bucket-name))
      (name bucket-name)
      obj-id)))


(defn get-download-url
  [obj file-name]
  (when obj
    (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
      (.presignedGetObject
        (get public-clients (keyword bucket-name))
        (name bucket-name)
        obj-id
        (int (* 7 24 3600))
        (new HashMap
             {"response-content-disposition" (format "attachment; filename=\"%s\"" file-name)})))))


(defn download
  [obj]
  (when obj
    (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
      (.getObject
        (get clients (keyword bucket-name))
        (name bucket-name)
        obj-id))))


(defn get-private-url
  [obj]
  (when obj
    (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
      (.presignedGetObject
        (get clients (keyword bucket-name))
        (name bucket-name)
        obj-id))))


(defn get-content-type
  [obj]
  (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
    (.contentType
      (.statObject
        (get clients (keyword bucket-name))
        (name bucket-name)
        obj-id))))


(defn get-content-size
  [obj]
  (let [[obj-id bucket-name] (obj->obj-id-bucket-name obj)]
    (.length
      (.statObject
        (get clients (keyword bucket-name))
        (name bucket-name)
        obj-id))))



(defn exist? [obj]
  (try
    (get-content-type obj)
    true
    (catch Exception e
      (let [code (-> (.errorResponse e) (.errorCode))]
        (if (or (= code ErrorCode/NO_SUCH_KEY) (= code ErrorCode/NO_SUCH_OBJECT))
          false
          (throw e))))))


(defn delete-temp-videos [_]
  (let [now (util/now)]
    (try
      (redis/locking-video
        (log/info "Deleting old videos from redis and minio")
        (doseq [video (redis/list-temp-videos)]
          (when (.isBefore (:create_time video) (.minusDays now 1))
            (redis/delete-temp-video (:id video))
            (remove-object (:id video))
            (remove-object (:thumbnail_id video)))))
      (catch Exception _
        (log/error "Error occurred while clearing old temp videos"))))
  true)



(defstate video-ticker
  :stop (async/close! video-ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (periodic-seq (time/days 1)))
           {:ch (async/chan (async/sliding-buffer 1))}))


(defstate video-runner
  :start (when (get-in env [:asbt :url])
           (async/pipeline-blocking
             1 (async/chan (async/sliding-buffer 1)) (map #'delete-temp-videos) video-ticker)))

(defn- copy-videos [from to]
  (let [[obj-id bucket-name] (obj->obj-id-bucket-name from)
        from-client (get public-clients (keyword bucket-name))
        [new-id new-bucket-name] (obj->obj-id-bucket-name to)
        to-client   (get public-clients (keyword new-bucket-name))]
    (.putObject to-client
                new-bucket-name
                new-id
                (.getObject from-client bucket-name obj-id)
                (get-content-type from))))

(comment
  (copy-videos "small-jarima/d46dcecc0b97442287d8575dabdb9ed3.mp4"
               "jarima/d46dcecc0b97442287d8575dabdb9ed3333.mp4"))


(defn- move-videos [from to condition limit]
  (->> {:select   [:id :video :extra_video :create_time]
        :from     [:report_offense_count]
        :order-by [[:create_time :asc]]
        :where    [:and
                   [:= :status "reviewed"]
                   condition
                   [:or
                    [:and
                     [:!= nil :video]
                     [:like :video (str from "/%")]]
                    [:and
                     [:!= nil :extra_video]
                     [:like :extra_video (str from "/%")]]]]
        :limit    limit}
       (db/query)
       (pmap
         (fn [{:keys [id video extra_video] :as report}]
           (let [video-future       (future
                                      (when (and video (not (str/starts-with? video (str to "/"))))
                                        (let [[obj-id bucket-name] (obj->obj-id-bucket-name video)
                                              new-video-id (str to "/" obj-id)]
                                          (log/info (str "Moving video " video " to " new-video-id))
                                          (copy-videos video new-video-id)
                                          (-> (query/update-report id {:video new-video-id})
                                              (db/query))
                                          (remove-object video))))
                 extra_video-future (future
                                      (when (and extra_video (not (str/starts-with? extra_video (str to "/"))))
                                        (let [[obj-id bucket-name] (obj->obj-id-bucket-name extra_video)
                                              new-extra-video-id (str to "/" obj-id)]
                                          (log/info (str "Moving extra video " extra_video " to " new-extra-video-id))
                                          (copy-videos extra_video new-extra-video-id)
                                          (-> (query/update-report id {:extra_video new-extra-video-id})
                                              (db/query))
                                          (remove-object extra_video))))]
             (doseq [f [video-future extra_video-future]]
               (when f
                 (try
                   (deref f)
                   (catch Exception e
                     (log/error e "Error occurred while moving video"))))))
           report))))

(comment
  "move new jarima videos to mvd"
  (move-videos
    "jarima"
    "mvd"
    [:> :create_time (util/parse-local-date-short "01.09.2022")]
    10000))

(comment
  "move ancient videos from jarima to archive"
  (move-videos
    "jarima"
    "archive"
    [:< :create_time (util/parse-local-date-short "01.06.2022")]
    10000))

(comment
  "move old mvd videos to jarima"
  (move-videos
    "mvd"
    "jarima"
    [:< :create_time (util/parse-local-date-short "01.06.2022")]
    10000))

(comment
  (->> {:select   [:id :video]
        :from     [:report_offense_count]
        :order-by [[:create_time :asc]]
        :where    [:and
                   [:= :status "reviewed"]
                   [:< :create_time (util/parse-local-date-short "01.06.2022")]
                   [:= :paid_count :total_count]
                   [:and
                    [:!= nil :video]
                    [:like :video "mvd%"]]]
        :limit    50000}
       (db/query)
       (partition-all 4)
       (map (fn [reports]
              (->> reports
                   (pmap
                     (fn [{:keys [id video] :as report}]
                       (try
                         (let [[obj-id bucket-name] (obj->obj-id-bucket-name video)
                               new-video-id (str "archive/" obj-id)]
                           (log/info (str "Moving video " video " to " new-video-id))
                           (copy-videos video new-video-id)
                           (-> (query/update-report id {:video new-video-id})
                               (db/query))
                           (remove-object video))
                         (catch Exception e
                           (log/error e "Failed to copy video")))
                       report)))))))


(defn- delete-old-accepted-videos []
  (let [old-reports (-> {
                         :select [:*]
                         ;:select [:%count.*]
                         :from   [:report_offense_count]
                         :limit 100
                         :where  [:and
                                  [:= :status "reviewed"]
                                  [:< :create_time (.minusMonths (util/now) 5)]
                                  #_[:= :paid_count :total_count]
                                  [:= [:+
                                       :dissmissed_count
                                       [:+
                                        :failed_count
                                        [:+ :paid_count
                                         :reject_count]]]

                                   :total_count]
                                  [:or
                                   [:!= nil :video]
                                   [:!= nil :extra_video]]]}
                        (db/query))]
    (map (fn [report]
           (log/info (format "Deleting videos for %s..." (:id report)))
           [(:id report)
            (when-let [video (:video report)]
              (remove-object video)
              (remove-object (util/replace-ext video "detections" "json"))
              (-> (query/update-report (:id report) {:video nil})
                  (db/query)))
            (when-let [video (:extra_video report)]
              (remove-object video)
              (remove-object (util/replace-ext video "detections" "json"))
              (-> (query/update-report (:id report) {:extra_video nil})
                  (db/query)))])
         old-reports)))



(defn- delete-old-failed-videos []
  (let [old-reports (-> {:select [:*]
                         :from   [:report_offense_count]
                         :where  [:and
                                  [:= :status "reviewed"]
                                  [:< :create_time (util/parse-local-date-short "31.10.2023")]
                                  #_[:= :paid_count :total_count]
                                  [:= [:+ :failed_count [:+ :paid_count :reject_count]] :total_count]
                                  [:or
                                   [:!= nil :video]
                                   [:!= nil :extra_video]]]}
                        (db/query))]
    (map (fn [report]
           (log/info (format "Deleting videos for %s..." (:id report)))
           [(:id report)
            (when-let [video (:video report)]
              (remove-object video)
              (remove-object (util/replace-ext video "detections" "json"))
              (-> (query/update-report (:id report) {:video nil})
                  (db/query)))
            (when-let [video (:extra_video report)]
              (remove-object video)
              (remove-object (util/replace-ext video "detections" "json"))
              (-> (query/update-report (:id report) {:extra_video nil})
                  (db/query)))])
         old-reports)))
(comment
  (delete-old-accepted-videos)
  (get-public-url "temporary-2021/292f2ced450b4d7aafddcb0324125625.mp4"))


(comment
  (let [batch-size  500
        total-count (-> {:select [[:%count.* :count]]
                         :from   [:report]
                         :where  [:or
                                  [:and
                                   [:!= nil :video]
                                   [:= nil :video_size]]
                                  [:and
                                   [:!= nil :extra_video]
                                   [:= nil :extra_video_size]]]}
                        (db/query-first :count))]
    (doseq [i (range 0 (inc (/ total-count batch-size)))]
      (let [videos (-> {:select [:video :id]
                        :from   [:report]
                        :limit  batch-size
                        :where  [:and
                                 [:!= nil :video]
                                 [:= nil :video_size]]}
                       (db/query))
            sizes  (pmap (fn [{:keys [video id]}]
                           {:id id :video_size (or (util/mute (get-content-size video)) 0)})
                         videos)]
        (doseq [size sizes]
          #_(log/info (format "Updating video size for %s..." (:id size)))
          (-> (query/update-report (:id size) {:video_size (:video_size size)})
              (db/query))))
      (let [extra_videos (-> {:select [:extra_video :id]
                              :from   [:report]
                              :limit  batch-size
                              :where  [:and
                                       [:!= nil :extra_video]
                                       [:= nil :extra_video_size]]}
                             (db/query))
            sizes        (pmap (fn [{:keys [extra_video id]}]
                                 {:id id :extra_video_size (or (util/mute (get-content-size extra_video)) 0)})
                               extra_videos)]
        (doseq [size sizes]
          #_(log/info (format "Updating extra video size for %s..." (:id size)))
          (-> (query/update-report (:id size) {:extra_video_size (:extra_video_size size)})
              (db/query)))))))


(comment
  (let [videos     (-> {:select [:video :extra_video]
                        :from   [:report]
                        :where  [:and
                                 [:= :status "reviewed"]
                                 [:< :create_time (.minusMonths (util/today) 24)]
                                 ;[:= :paid_count :total_count]
                                 [:!= nil :video]
                                 [:not
                                  [:like :video "%mvd/%"]]]}
                       (db/query))
        sizes      (pmap
                     (fn [row]
                       {:size   (or (util/mute
                                      (get-content-size (:video row)))
                                    0)
                        :origin (-> (:video row)
                                    (str/split #"/")
                                    (first))})
                     videos)
        total-size (reduce (fn [acc size]
                             (update acc (:origin size)
                                     (fn [prev-count]
                                       (+ (or prev-count 0)
                                          (:size size)))))
                           {}
                           sizes)]
    total-size))

(comment
  (-> {:select [:%count.*]
       :from   [:report]
       :where  [:and
                [:= :status "reviewed"]
                [:< :create_time (.minusMonths (util/today) 24)]
                ;[:= :paid_count :total_count]
                [:!= nil :video]]}
      (db/query)))


(defn run [_]
  (->
    (delete-old-accepted-videos)
    (dorun))
  true)


(defstate ticker
  :stop (async/close! ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (periodic-seq (time/seconds 600)))
           {:ch (async/chan (async/sliding-buffer 1))}))


(defstate runner
  :start (async/pipeline-blocking
           1 (async/chan (async/sliding-buffer 1)) (map #'run) ticker))