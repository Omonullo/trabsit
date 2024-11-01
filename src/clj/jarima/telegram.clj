(ns jarima.telegram
  (:require [chime]
            [mount.core :as mount]
            [clj-time.core :as time]
            [jarima.config :refer [env]]
            [clojure.core.async :as a]
            [clj-http.client :as client]
            [clj-time.periodic :refer [periodic-seq]]
            [jarima.db.query :as q]
            [jarima.db.core :as db]
            [honeysql.core :as sql]
            [cuerdas.core :as str]
            [jarima.util :as util]
            [selmer.parser :refer [render-file]])
  (:import [org.joda.time DateTime]
           [java.time Month]
           [java.util Locale]
           [java.time.format TextStyle]))


(defn send-message
  [chat text]
  (when (some? (-> env :telegram :base-url))
    (client/post
      (str (-> env :telegram :base-url) "/sendMessage")
      {:form-params {:chat_id    chat
                     :text       text
                     :parse_mode "markdown"}})))


(defn stat
  [area date]
  (merge
    (db/query-first
      {:select    [[(sql/raw "count (distinct report_id)") :new]]
       :from      [:offense]
       :left-join [:report [:= :report.id :report_id]]
       :where     [:and
                   [:= :report.area_id area]
                   [:= date [:date-trunc :day :report.create_time]]]})
    (db/query-first
      {:select    [[(sql/raw "count (distinct report_id)") :accepted] [:%sum.fine :fine]]
       :from      [:offense]
       :left-join [:report [:= :report.id :report_id]]
       :where     [:and
                   [:= :report.area_id area]
                   [:= date [:date-trunc :day :offense.accept_time]]]})
    (db/query-first
      {:select    [[(sql/raw "count (distinct report_id)") :rejected]]
       :from      [:offense]
       :left-join [:report [:= :report.id :report_id]]
       :where     [:and
                   [:= :report.area_id area]
                   [:= date [:date-trunc :day :offense.reject_time]]]})
    (db/query-first
      {:select    [[:%count.report.id :reviewed]]
       :from      [:report]
       :where     [:and
                   [:= :report.area_id area]
                   [:= date [:date-trunc :day :report.review_time]]]})
    (db/query-first
      {:select    [[(sql/raw "count (distinct report_id)") :total_not_reviewed]]
       :from      [:offense]
       :left-join [:report [:= :report.id :report_id]]
       :where     [:and
                   [:= :report.area_id area]
                   [:= "created" :offense.status]]})
    (db/query-first
      {:select    [[:%count.* :total_failed_today]]
       :from      [:offense]
       :left-join [:report [:= :report.id :report_id]]
       :where     [:and
                   [:= date [:date-trunc :day :offense.accept_time]]
                   [:= :report.area_id area]
                   [:= "failed" :offense.status]]})
    (db/query-first
      {:select    [[:%count.* :total_failed]]
       :from      [:offense]
       :left-join [:report [:= :report.id :report_id]]
       :where     [:and
                   [:= :report.area_id area]
                   [:= "failed" :offense.status]]})))


(defn month-name
  [time]
  (-> (Month/of (.getMonthValue time))
      (.getDisplayName TextStyle/FULL_STANDALONE (Locale. "ru"))))


(defn run [date]
  (->> (q/select-area {})
       (db/query)
       (map
         (fn [{:keys [id name_uz_cy]}]
           (merge
             (stat id date)
             {:date date
              :area name_uz_cy
              :tag  {:month (month-name date)
                     :area  (str/lower (str/replace name_uz_cy #"\s+" "\\\\_"))}})))
       (map (partial render-file "telegram/summary.md"))
       (map (fn [text]
              (doseq [id (-> env :telegram :chat-ids)]
                (send-message id text))))
       (dorun))
  1)


(mount/defstate ticker
  :stop (a/close! ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (.withTime 8 0 0 0)
               (periodic-seq (time/days 1)))
           {:ch (a/chan (a/sliding-buffer 1))}))


(comment
  (let [id #uuid"811a6aa2-2446-4abc-b91a-7c8b61310721"
        date (-> (java.time.LocalDate/now)
                 (.minusDays 13))]
    (stat id date)))

(comment
  (-> (first (-> (DateTime/now (time/default-time-zone))
                 (.withTime 8 0 0 0)
                 (periodic-seq (time/days 1))))
      .toLocalDateTime
      util/->java-local-date-time
      .toLocalDate
      (.minusDays 1)
      run))

(mount/defstate runner
  :start (a/pipeline-blocking
           1
           (a/chan (a/sliding-buffer 1))
           (map #(-> % .toLocalDateTime util/->java-local-date-time .toLocalDate (.minusDays 1) run))
           ticker))
