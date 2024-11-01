(ns jarima.handler.staff.statistics
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.kash :as kash]
    [jarima.util :as util]
    [jarima.uzcard :as uzcard]
    [jarima.db.core :as db]
    [clojure.string :as str]
    [medley.core :refer :all]
    [jarima.universal :as universal]
    [jarima.db.query :as query]
    [jarima.layout :refer [t-get t]]
    [jarima.layout :as layout :refer [t]]
    [medley.core :refer [update-existing]]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [jarima.handler.staff.report :refer [restrict-filter]]
    [mount.core :as mount]))


(mount/defstate balance
  :start (atom {}))


(mount/defstate balance-poll
  :stop (balance-poll)
  :start (util/graceful-loop (* 1000 60 10)
           (future (swap! balance assoc :kash (kash/balance)))
           (future (swap! balance assoc :uzcard (uzcard/balance)))))


(defn index
  [req]
  (let [{:keys [role area_id]} (:identity req)]
    (layout/render
      "admin/statistic/index.html"
      {:balance (when (= role "admin") @balance)
       :areas   (->> (query/select-area
                       {:id (when (and area_id (= role "inspector"))
                              [area_id])})
                     (db/query)
                     (map (fn [area]
                            {:name (t-get area :name)
                             :id   (:id area)})))})))


(defn expired-reports-stats
  [req]
  (->> (restrict-filter {} (:identity req))
       (query/expired-reports-stats
         (range 3 14) (if (-> req :identity :role (= "inspector")) :district :area))
       (db/query)
       (map (fn [row] (-> row
                          (dissoc :name_ru :name_uz_la :name_uz_cy)
                          (assoc :area_name (t-get row :name)))))
       (response/ok)))


(defn offenses-stats
  [{query :parameters :as req}]
  (let [{:keys [role area_id]} (:identity req)
        query (if (and (= role "inspector") (some? area_id))
                (query/offense-stats :district (merge {:area_id [area_id]} (:query query)))
                (query/offense-stats :area (:query query)))]
    (->> (db/query query)
         (map (fn [area]
                (-> (select-keys area [:report_count
                                       :count
                                       :accepted_count
                                       :rejected_count
                                       :pending_count
                                       :unpaid_fine_sum
                                       :unpaid_fine_count
                                       :expired_fine_sum
                                       :expired_fine_count
                                       :paid_fine_sum
                                       :paid_fine_count
                                       :fine_sum
                                       :fine_count])
                    (assoc :area_name (t-get area :name)))))
         (response/ok))))


(defn offenses-funnel
  [{query :parameters :as req}]
  (let [{:keys [role area_id]} (:identity req)
        query (if (and (= role "inspector") (some? area_id))
                (query/offenses-funnel :district (merge {:today (util/today) :area_id [area_id]} (:query query)))
                (query/offenses-funnel :area (merge {:today (util/today)} (:query query))))]
    (->> (db/query query)
         (map (fn [area] (assoc area :area_name (t-get area :name))))
         (response/ok))))


(defn articles-funnel
  [{query :parameters}]
  (->> (query/article-funnel :area (merge {:today (util/today)} (:query query)))
       (db/query)
       (map (fn [article] (assoc article :text (t-get article :text)
                                         :alias (t-get article :alias))))
       (response/ok)))


(defn reviewed-reports-stats
  [req]
  (->> (restrict-filter {} (:identity req))
       (query/reviewed-reports-stats (map #(-> (util/today) (.minusDays (dec %))) [1 3 7 30]))
       (db/query)
       (map (fn [row]
              (-> (select-keys row [:count_1 :count_2 :count_3 :count_4 :total :area_id])
                  (assoc :area_name (or (t-get row :name) (t "Не привязан")))
                  (assoc :inspector_name (str/join " " (vals (select-keys row [:first_name :last_name :middle_name])))))))
       (response/ok)))


(defn fines-rewards-stats
  [req]
  (->> (restrict-filter (:query (:parameters req)) (:identity req))
       (query/fines-rewards-stats)
       (db/query)
       (map #(assoc %2 :number (inc %1)) (range))
       (response/ok)))


(defn article-list
  [_]
  (->> (query/select-article nil)
       (db/query)
       (map #(assoc %
               :text (layout/t-get % :text)
               :alias (layout/t-get % :alias)))
       (response/ok)))


(defn offense-points
  [req]
  (let [{:keys [role area_id]} (:identity req)
        date-range (get-in req [:parameters :query :date_range])
        article-ids (some->>
                      (get-in req [:parameters :query :article_ids])
                      (map (fn [x]
                             (try (Long/parseLong x)
                                  (catch Exception _))))
                      (filter some?))
        points (->> {:select   [:report.lat
                                :report.lng
                                :report.incident_time
                                [:report.id :report_id]
                                :offense.number
                                [:article.id :article_id]
                                [:article.number :article_number]]
                     :from     [:offense]
                     :join     [:article [:= :article.id :offense.article_id]
                                :report [:= :report.id :offense.report_id]]
                     :where    [:and
                                [:within :report.incident_time date-range]
                                (when (seq article-ids)
                                  [:in :article.id article-ids])
                                (when (and area_id (= role "inspector"))
                                  [:= :report.area_id area_id])]
                     :order-by [[:report.incident_time :asc]]}
                    (db/query))]
    (response/ok {:points points})))


(defn offense-map
  [req]
  (layout/render "admin/statistic/offense-map.html"
    {:google-maps-key (:google-maps-key env)}))


(def routes
  [""
   ["/statistics"
    {:name :staff.statistics/index
     :get  #'index}]
   ["/offense-map"
    {:name       :staff.statistics/offenses-map
     :get        #'offense-map}]])


(def api-routes
  [""
   ["/statistics"
    ["/offenses"
     {:name       :staff.statistics/offenses
      :get        #'offenses-stats
      :parameters {:query :staff.statistics/date_range}}]
    ["/offenses-funnel"
     {:name       :staff.statistics/offenses-funnel
      :get        #'offenses-funnel
      :parameters {:query :staff.statistics/date_range}}]
    ["/articles-funnel"
     {:name       :staff.statistics/articles-funnel
      :get        #'articles-funnel
      :parameters {:query :staff.statistics/date_range_and_area_id}}]
    ["/fines-rewards"
     {:name       :staff.statistics/fines-rewards
      :get        #'fines-rewards-stats
      :parameters {:query :staff.statistics/date_range}}]
    ["/expired-reports"
     {:name :staff.statistics/expired-reports
      :get  #'expired-reports-stats}]
    ["/reviewed-reports"
     {:name :staff.statistics/reviewed-reports
      :get  #'reviewed-reports-stats}]]
   ["/articles"
    {:name :staff.api/articles
     :get  #'article-list}]
   ["/offense-points"
    {:name       :staff.api/offenses-map
     :get        #'offense-points
     :parameters {:query :staff.offense_points/query}}]])

(def public-api-routes
  [""
   ["/offense-points"
    {:name       :staff.public-api/offenses-map
     :get        #'offense-points
     :parameters {:query :staff.offense_points/query}}]
   ["/articles"
    {:name :staff.public-api/articles
     :get  #'article-list}]])

