(ns jarima.asbt
  (:import [org.joda.time DateTime]
           (javax.imageio ImageIO)
           (java.io ByteArrayOutputStream)
           (java.awt.image BufferedImage))
  (:require [chime]
            [jarima.util :as util]
            [image-resizer.util :as resizer.util]
            [image-resizer.core :as resizer.core]
            [jarima.db.query :as q]
            [honeysql.core :as sql]
            [jarima.db.core :as db]
            [clj-time.core :as time]
            [jarima.minio :as minio]
            [medley.core :refer [uuid]]
            [clj-http.client :as client]
            [clojure.core.async :as async]
            [jarima.http-log :as http-log]
            [clojure.core.memoize :as memo]
            [clojure.tools.logging :as log]
            [cprop.core :refer [load-config]]
            [mount.core :refer [args defstate]]
            [conman.core :refer [with-transaction]]
            [jarima.config :refer [dictionary env]]
            [clj-time.periodic :refer [periodic-seq]]
            [clojure.string :as str]))

(def get-token
  (memo/ttl
    (fn []
      (log/info "Getting access token...")
      (->
        (get-in env [:asbt :auth-url])
        (client/post
          {:as              :json
           :decompress-body false
           :headers         {"Accept-Encoding" "identity"}
           :form-params     {:username   (get-in env [:asbt :auth-login])
                             :password   (get-in env [:asbt :auth-password])
                             :grant_type "password"}})
        (:body)
        (:access_token)
        (doto (assert "Failed to get access token"))))
    {} :ttl/threshold (-> 24 (* 60) (* 60) (* 1000))))


(defn offense->short-id
  [{:keys [number create_time]}]
  (format "%02d%d%02d"
          (.getMinute create_time)
          number
          (.getSecond create_time)))


(defn short-id->offense-id [id]
  (when-let [[mm number ss]
             (->> (or id "")
                  (re-matches #"(\d{2})(\d+)(\d{2})")
                  (rest)
                  (map util/parse-int))]
    (db/query-first {:select [:id]
                     :from   [:offense]
                     :where  [:and
                              [:= :number number]
                              [:= mm [:extract :minute :create_time]]
                              [:= ss [:cast [:floor [:extract :second :create_time]] :int]]]} :id)))


(defn request-params
  [{:keys [id phone lat lng region district region_name district_name place plate violation review_time incident_time plate_img img extra_img
           inspector_phone inspector_first_name inspector_last_name inspector_middle_name inspector_rank] :as offense}]
  {:pId                  (util/uuid->guid id)
   :pLink                (str (:base-url env) "/r/" (offense->short-id offense))
   :pPhone               phone
   :pInspectorPhone      inspector_phone
   :pInspectorFirstName  inspector_first_name
   :pInspectorLastName   inspector_last_name
   :pInspectorMiddleName inspector_middle_name
   :pInspectorRank       inspector_rank
   :pReviewDate          (util/unparse-local-date-short review_time)
   :pReviewTime          (util/unparse-local-time review_time)
   :pPlaceLatitude       (str lat)
   :pPlaceLongitude      (str lng)
   :pRegion              region
   :pDistrict            district
   :pPlace               (str/join " " (filter not-empty [region_name district_name place]))
   :pPlateNumber         plate
   :pViolationType       violation
   :pViolationDate       (util/unparse-local-date-short incident_time)
   :pViolationTime       (util/unparse-local-time incident_time)
   :pPhotoPlate          (util/image->base64 (minio/download plate_img) 30)
   :pPhoto               (util/image->base64 (minio/download img) 200)
   :pPhotoAdditional     (some-> (minio/download extra_img) (util/image->base64 200))})


(defn forward-offense
  [offense]
  (log/info "Forwarding offense" (:number offense) "...")
  (binding [http-log/*logs* (atom [])]
    (try
      (let [{code    :AnswereId
             message :AnswereMessage
             comment :AnswereComment}
            (:body (http-log/capture-logs
                     #(client/post (get-in env [:asbt :url])
                        {:content-type    :json
                         :as              :json
                         :json-opts       {:pretty true}
                         :decompress-body false
                         :form-params     (request-params offense)
                         :headers         {"Authorization"   (str "Bearer " (get-token))
                                           "Accept-Encoding" "identity"}})))]
        (when (not= 1 code)
          (throw (Exception. (str message ": " comment))))
        (db/query
          {:returning [:*]
           :where     [:= :id (:id offense)]
           :update    :offense
           :set       {:forward_time    (util/now)
                       :status          "forwarded"
                       :failure_message nil}})
        (log/info "Offense forward succeeded" (:id offense)))
      (catch Throwable t
        (log/warn "Offense forward failed" (:id offense) (ex-message t))
        (db/query
          {:returning [:*]
           :where     [:= :id (:id offense)]
           :update    :offense
           :set       {:failure_time    (util/now)
                       :status          "failed"
                       :failure_message (let [error-message (ex-message t)]
                                          (if (and error-message (some? (re-find #"Ходиса вақти ҳато берилган\s*Қоидабузарлик санаси 30 кундан ўтиб кетган" error-message)))
                                            "Ходиса вақти ҳато берилган\nҚоидабузарлик санаси 30 кундан ўтиб кетган"
                                            (or error-message "Something went wrong")))}}))
      (finally
        (db/query {:returning [:*]
                   :update    :offense
                   :where     [:= :id (:id offense)]
                   :set       {:asbt_log (sql/call
                                           :concat_ws "\n\n\n"
                                           :asbt_log (str
                                                       (util/unparse-local-date-time (util/now)) "\n"
                                                       (apply str @http-log/*logs*)))}})))))



(defn image-to-bytes [^BufferedImage image]
  (let [baos (ByteArrayOutputStream.)
        _ (ImageIO/write image "jpg" baos)
        _ (.close baos)]
    (.toByteArray baos)))

(defn resize-image [^BufferedImage image ^double coefficient]
  (let [width (first (resizer.core/dimensions image))]
    (resizer.core/resize-to-width image (* coefficient width))))

(comment
  (let [image-id "mvd/ae3a883a-f75a-4d25-bedf-334620772983.jpg"]
    (-> image-id
        (minio/download)
        (resizer.util/buffered-image)
        (resize-image 2)
        (image-to-bytes)
        (minio/upload-jpg-with-id image-id))))


(comment
  (let [offenses (-> {:failure_message ["PhotoPlate бўш ёки узунлиги 1000 байтдан кам, ёки 10000 байтдан кўп: PhotoPlate бўш ёки узунлиги 1000 байтдан кам, ёки 10000 байтдан кўп"]}
                     (q/select-offense)
                     (db/query))
        id_imgs (map :vehicle_id_img offenses)
        offense-ids (distinct (map :id offenses))]
    (for [{:keys [vehicle_id_img id report_id]} offenses]
      (do
        (doall
          (-> vehicle_id_img
              (minio/download)
              (resizer.util/buffered-image)
              (image-to-bytes)
              (minio/upload-jpg-with-id (str vehicle_id_img "_backup"))))
        (doall
          (-> vehicle_id_img
              (minio/download)
              (resizer.util/buffered-image)
              (resize-image 2)
              (image-to-bytes)
              (minio/upload-jpg-with-id vehicle_id_img)
              (prn)))
        (doall
          (-> (q/update-offense id {:failure_message nil})
              (db/query)))
        report_id))))

(comment
  (let [offense (db/query-first {:select    [[:offense.id :id]
                                             [:offense.number :number]
                                             [:offense.create_time :create_time]
                                             [:citizen.phone :phone]
                                             [:staff.public_phone :inspector_phone]
                                             [:staff.first_name :inspector_first_name]
                                             [:staff.last_name :inspector_last_name]
                                             [:staff.middle_name :inspector_middle_name]
                                             [:staff.rank :inspector_rank]
                                             [:report.lat :lat]
                                             [:report.lng :lng]
                                             [:area.code :region]
                                             [:area.name_uz_cy :region_name]
                                             [:district.name_uz_cy :district_name]
                                             [:district.code :district]
                                             [:article.id :violation]
                                             [:vehicle.id :plate]
                                             [:report.address :place]
                                             [:report.incident_time :incident_time]
                                             [:report.review_time :review_time]
                                             [:offense.vehicle_id_img :plate_img]
                                             [:offense.vehicle_img :img]
                                             [:offense.extra_img :extra_img]]
                                 :from      [:offense]
                                 :limit     20
                                 :left-join [:article [:= :offense.article_id :article.id]
                                             :report [:= :offense.report_id :report.id]
                                             :staff [:= :report.inspector_id :staff.id]
                                             :citizen [:= :report.citizen_id :citizen.id]
                                             :district [:= :report.district_id :district.id]
                                             :area [:= :report.area_id :area.id]
                                             :vehicle [:= :offense.vehicle_id :vehicle.id]]
                                 :where     [:= :offense.number 219]})]
    (double (/ (count (:pPhotoPlate (request-params offense))) 1024))))


(defn run [now]
  (with-transaction [db/*db*]
    (-> {:select    [[:offense.id :id]
                     [:offense.number :number]
                     [:offense.create_time :create_time]
                     [:citizen.phone :phone]
                     [:staff.public_phone :inspector_phone]
                     [:staff.first_name :inspector_first_name]
                     [:staff.last_name :inspector_last_name]
                     [:staff.middle_name :inspector_middle_name]
                     [:staff.rank :inspector_rank]
                     [:report.lat :lat]
                     [:report.lng :lng]
                     [:area.code :region]
                     [:area.name_uz_cy :region_name]
                     [:district.name_uz_cy :district_name]
                     [:district.code :district]
                     [:article.id :violation]
                     [:vehicle.id :plate]
                     [:report.address :place]
                     [:report.incident_time :incident_time]
                     [:report.review_time :review_time]
                     [:offense.vehicle_id_img :plate_img]
                     [:offense.vehicle_img :img]
                     [:offense.extra_img :extra_img]]
         :from      [:offense]
         :limit     100
         :left-join [:article [:= :offense.article_id :article.id]
                     :report [:= :offense.report_id :report.id]
                     :staff [:= :report.inspector_id :staff.id]
                     :citizen [:= :report.citizen_id :citizen.id]
                     :district [:= :report.district_id :district.id]
                     :area [:= :report.area_id :area.id]
                     :vehicle [:= :offense.vehicle_id :vehicle.id]]
         :where     [:or
                     [:= "accepted" :offense.status] ; accepted offences
                     [:and [:= "failed" :offense.status] [:= nil :offense.failure_message]]]} ; failed but marked as to retry
        (q/for-update :offense :skip-locked true)
        (db/query forward-offense)
        (dorun)))
  now)



(defstate ticker
  :stop (async/close! ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (periodic-seq (time/seconds 5)))
           {:ch (async/chan (async/sliding-buffer 1))}))


(defstate runner
  :start (when (get-in env [:asbt :url])
           (async/pipeline-blocking
             1 (async/chan (async/sliding-buffer 1)) (map #'run) ticker)))
