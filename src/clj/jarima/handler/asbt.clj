(ns jarima.handler.asbt
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.util :as util]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [medley.core :refer :all]
    [jarima.layout :as layout]
    [jarima.validation :refer :all]
    [clojure.tools.logging :as log]
    [jarima.reward :refer [reward-type]]
    [ring.util.http-response :as response]
    [conman.core :refer [with-transaction]]
    [buddy.auth :refer [throw-unauthorized]]
    [jarima.ffmpeg :refer [extract-thumbnail]]
    [jarima.asbt :refer [short-id->offense-id]]
    [jarima.config :refer [dictionary env mwage]]
    [jarima.handler.staff.report :refer [video-source]]
    [jarima.spec :as spec]
    [clojure.string :as str]
    [jarima.minio :as minio]))


(defn view [request]
  (let [offense (-> {:id [(-> request :parameters :path :id short-id->offense-id)]}
                    (q/select-offense)
                    (db/query-first
                      (fn [offense]
                        (-> offense
                            (assoc :report
                                   (let [report
                                         (-> {:id [(:report_id offense)]}
                                             (q/select-report)
                                             (db/query)
                                             (first))]
                                     (-> report
                                         (update :video video-source (format "report_%d.mp4" (:number report)))
                                         (update :extra_video_type (comp :name spec/video-types))
                                         (update :extra_video video-source (format "extra_report_%d.mp4" (:number report))))))))))]
    (if (or (nil? offense) (nil? (:forward_time offense)))
      (layout/error-page!
        {:status 404
         :title  "Нарушение не найдено"})
      (layout/render "misc/asbt.html" {:offense offense}))))


(defn api-view [request]
  (let [id (-> request :parameters :path :id)
        offense (-> (if (str/starts-with? id "R")
                      {:fine_id [id]}
                      {:id [(short-id->offense-id id)]})
                    (q/select-offense)
                    (db/query-first))]
    (if (or (nil? offense) (nil? (:forward_time offense)))
      (response/not-found
        {:status 404
         :msg    "Нарушение не найдено"})
      (response/ok {:offense
                    (-> offense
                        (select-keys [:id
                                      :number
                                      :report_id
                                      :vehicle_id
                                      :article_id
                                      :extra_response
                                      :status
                                      :vehicle_img
                                      :vehicle_id_img
                                      :extra_img
                                      :fine_id
                                      :fine_date
                                      :response_id
                                      :fine

                                      :response_text_uz_cy
                                      :response_text_uz_la
                                      :response_text_ru

                                      :article_text_uz_cy
                                      :article_text_uz_la
                                      :article_text_ru
                                      :article_alias_uz_cy
                                      :article_alias_uz_la
                                      :article_alias_ru
                                      :article_number
                                      :article_factor
                                      :article_url])
                        (update :vehicle_img minio/get-public-url)
                        (update :vehicle_id_img minio/get-public-url)
                        (update :extra_img minio/get-public-url)
                        (assoc :report
                          (let [report
                                (-> {:id [(:report_id offense)]}
                                    (q/select-report)
                                    (db/query)
                                    (first))
                                area (-> {:id [(:area_id report)]}
                                         (q/select-area)
                                         (assoc :select [[:number :area_number]
                                                         [:name_ru :area_name_ru]
                                                         [:name_uz_cy :area_name_uz_cy]
                                                         [:yname_ru :area_yname_ru]
                                                         [:yname_uz_cy :area_yname_uz_cy]
                                                         [:code :area_code]
                                                         [:name_uz_la :area_name_uz_la]
                                                         [:yname_uz_la :area_yname_uz_la]])
                                         (db/query-first))
                                district (-> {:id [(:district_id report)]}
                                             (q/select-district)
                                             (assoc :select [[:district.number :district_number]
                                                             [:district.area_id :district_area_id]
                                                             [:district.name_ru :district_name_ru]
                                                             [:district.name_uz_cy :district_name_uz_cy]
                                                             [:district.yname_ru :district_yname_ru]
                                                             [:district.yname_uz_cy :district_yname_uz_cy]
                                                             [:district.code :district_code]
                                                             [:district.name_uz_la :district_name_uz_la]
                                                             [:district.yname_uz_la :district_yname_uz_la]])
                                             (db/query-first))]
                            (-> (merge report area district)
                                (select-keys
                                  [
                                   :forward_time
                                   :area_number
                                   :area_name_ru
                                   :area_name_uz_cy
                                   :area_yname_ru
                                   :area_yname_uz_cy
                                   :area_code
                                   :area_name_uz_la
                                   :area_yname_uz_la

                                   :district_number
                                   :district_area_id
                                   :district_name_ru
                                   :district_name_uz_cy
                                   :district_yname_ru
                                   :district_yname_uz_cy
                                   :district_code
                                   :district_name_uz_la
                                   :district_yname_uz_la

                                   :id
                                   :number
                                   :lat
                                   :lng

                                   :area_id
                                   :district_id

                                   :address
                                   :video
                                   :incident_time
                                   :create_time
                                   :status
                                   :extra_video
                                   :extra_video_type])
                                (update :video video-source (format "report_%d.mp4" (:number report)))
                                (update :extra_video_type (comp :name spec/video-types))
                                (update :extra_video video-source (format "extra_report_%d.mp4" (:number report)))))))}))))


(defn response
  [id message]
  {:AnswereId id :AnswereMessage message})


(defn assoc-fine-and-reward
  [offense]
  (when (not (:article_factor offense))
    (log/error "Offence was not accepted, but received payment" offense))
  (-> offense
      (update :fine #(or % (when (:article_factor offense)
                             (* (:article_factor offense) @mwage))))
      (update :reward_amount #(or % (* 0.05 @mwage)))))


(defn assoc-reward-id
  [offense report now]
  (let [founder (-> {:id [(:creator_staff_id offense)]}
                    (q/select-staff)
                    (db/query-first))
        reviewer (-> {:id [(:inspector_id report)]}
                     (q/select-staff)
                     (db/query-first))]

    (-> offense
        (update :reward_id
                #(or %
                     (-> (cond
                           (:card founder)
                           {:id          (random-uuid)
                            :type        "card"
                            :amount      (:reward_amount offense)
                            :params      {:card true}
                            :number      (:number offense)
                            :staff_id    (:id founder)
                            :status      "created"
                            :create_time now}

                           (and (-> report :reward_params :no-reward) (:card reviewer))
                           {:id          (random-uuid)
                            :type        "card"
                            :amount      (:reward_amount offense)
                            :params      {:card true}
                            :number      (:number offense)
                            :staff_id    (:id reviewer)
                            :status      "created"
                            :create_time now}

                           :else
                           {:id          (random-uuid)
                            :type        (reward-type (:reward_params report))
                            :amount      (:reward_amount offense)
                            :params      (:reward_params report)
                            :number      (:number offense)
                            :status      "created"
                            :create_time now})
                         (q/insert-reward)
                         (db/query-first :id)))))))


(defn notify
  [{params :params identity :identity}]
  (when-not (= "asbt" (:role identity))
    (throw-unauthorized))
  (log/info "Received asbt notification" params)
  (when-let [errors (validate-asbt-notification params)]
    (-> (response 3 (util/pretty-errors errors))
        (response/bad-request)
        (response/throw!)))
  (with-transaction [db/*db*]
    (let [{id :pId status :pStatus date :pDate serial :pSeryNumber} params
          offense (db/query-first (q/select-offense {:id [(util/guid->uuid id)]}))
          report (db/query-first (q/select-report {:id [(:report_id offense)]}))
          now (util/now)]
      (try
        (->> (case status
               201 (-> (merge offense {:status   "paid"
                                       :pay_time now})
                       (assoc-fine-and-reward)
                       (assoc-reward-id report now)
                       (select-keys [:status :pay_time :fine :reward_amount :reward_id]))
               208 {:status       "dismissed"
                    :dismiss_time now}
               209 (-> (merge offense
                              {:status       "forwarded"
                               :forward_time (util/now)
                               :fine_id      serial
                               :fine_date    (util/parse-local-date-short date)})
                       (assoc-fine-and-reward)
                       (select-keys [:status :forward_time :fine_id :fine_date :fine :reward_amount])))
             (q/update-offense (:id offense))
             (db/query))
        (response/ok (response 1 "Ok"))
        (catch Exception e
          (log/error e)
          (response/internal-server-error
            (response 2 (ex-message e))))))))


(comment
  (let [now (util/now)
        ;; Jarima Dev
        asbt-notify {:pId "a003ad6de4644e30b805817d4e94c4e3", :pDate "07.11.2020", :pStatus 201, :pSeryNumber "RR220396692979333"}
        {id :pId status :pStatus date :pDate serial :pSeryNumber} asbt-notify
        offense (db/query-first (q/select-offense {:id [(util/guid->uuid "dade44e4aac54f439730f22ab73eadca")]}))
        report (db/query-first (q/select-report {:id [(:report_id offense)]}))]
    (-> (merge offense {:status   "paid"
                        :pay_time now})
        (assoc-fine-and-reward)
        (assoc-reward-id report now)
        :reward_id)))


(def routes
  [["/offense/notify"
    {:name :asbt/notify
     :post #'notify}]
   ["/r/:id"
    {:name       :asbt/view
     :get        #'view
     :parameters {:path {:id string?}}}]
   ["/api/r/:id"
    {:name       :asbt.api/view
     :get        #'api-view
     :parameters {:path {:id string?}}}]])
