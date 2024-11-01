(ns jarima.handler.citizen.offense
  (:require
    [jarima.util :as util]
    [jarima.spec :as spec]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [jarima.minio :as minio]
    [jarima.layout :as layout :refer [t]]))


(defn before-index
  [request]
  (update-in
    request [:parameters :query :sort]
    (fn [v]
      (or v (if (= "admin" (-> request :identity :role))
              "create_time_desc"
              "create_time_asc")))))

(defn restrict-filter
  [filter identity]
  (assoc filter :founder_role "citizen"
                :citizen_id (:id identity)))


(defn index
  [{{query :query} :parameters identity :identity :as request}]
  (layout/render
    "citizen/offense/list.html"
    (-> (util/paged-query
          (partial q/select-offense
                   (-> query
                       (restrict-filter identity)
                       (util/vectorify-vals (complement #{:founder_role :vehicle_id :create_time :sort :incident_time}))))
          (:page query))
        (assoc :request request)
        (assoc :statuses (keys spec/offense-statuses))
        (assoc :articles (db/query (q/select-article {})))
        (update :paged-rows
                (partial map
                         (fn [offense]
                           (assoc offense :report
                             (let [report
                                   (-> {:id [(:report_id offense)]}
                                       (q/select-report)
                                       (db/query)
                                       (first))]
                               (-> report
                                   (update :thumbnail minio/get-public-url)
                                   (assoc :citizen (-> {:id [(:citizen_id report)]}
                                                       (q/select-citizen)
                                                       (db/query)
                                                       (first))))))))))))


(def routes
  ["/offenses"
   [""
    {:name       :citizen.offense/index
     :get        (comp #'index #'before-index)
     :parameters {:query :citizen.offense.index/query}}]])

