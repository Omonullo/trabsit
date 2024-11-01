(ns jarima.handler.staff.reward
  (:require
    [jarima.util :as util]
    [jarima.spec :as spec]
    [cuerdas.core :as str]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [jarima.minio :as minio]
    [medley.core :refer [index-by]]
    [clojure.tools.logging :as log]
    [jarima.reward :refer [reward-type]]
    [jarima.layout :as layout :refer [t]]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [dk.ative.docjure.spreadsheet :as excel]
    [ring.util.io :refer [piped-input-stream]]
    [jarima.handler.staff.report :as staff.report]
    [medley.core :refer [uuid random-uuid map-vals filter-vals]]))


(defn index
  [{{query :query} :parameters identity :identity}]
  (layout/render
    "staff/reward/list.html"
    (-> (util/paged-query
          (partial q/select-reward
                   (-> query
                       (assoc :staff_id [(:id identity)])
                       (util/vectorify-vals (complement #{:create_time}))))
          (:page query)
          [:%sum.amount :amount])
        (assoc :statuses (keys spec/reward-statuses))
        (assoc :types spec/reward-types))))

(defn card-form [request]
  (layout/render
    "staff/reward/form.html"
    {:staff (-> {:id [(:id (:identity request))]}
                (q/select-staff)
                (db/query-first))}))


(defn update-card [request]
  (do (-> (:id (:identity request))
          (q/update-staff {:card (-> request :parameters :form :card)})
          (db/query))
      (response/found "/")))


(defn view [request]
  (let [reward
        (-> {:id       [(-> request :parameters :path :id)]
             :staff_id [(-> request :identity :id)]}
            (q/select-reward)
            (db/query-first
              (fn [reward]
                (-> reward
                    (merge
                      {:report  (-> {:id [(:report_id reward)]}
                                    (q/select-report)
                                    (db/query-first))
                       :offense (-> {:reward_id [(:id reward)]}
                                    (q/select-offense)
                                    (db/query-first))
                       :staff   (-> {:id [(:staff_id reward)]}
                                    (q/select-staff)
                                    (db/query-first))})))))]

    (when-not reward
      (layout/error-page!
        {:status 404
         :title  "Вознаграждение не найден"}))
    (layout/render
      "staff/reward/view.html"
      {:reward reward})))


(defn repay [request]
  (let [reward (-> {:id       (-> request :parameters :path :id)
                    :status   "failed"
                    :staff_id (:id (:identity request))}
                   (util/vectorify-vals)
                   (q/select-reward)
                   (db/query-first))]
    (when-not reward
      (layout/error-page!
        {:status 404
         :title  "Вознаграждение не найден"}))
    (db/execute
      {:update    :reward
       :returning [:*]
       :where     [:and [:!= "paid" :status] [:= :id (:id reward)]]
       :set       {:failure_message nil :transaction_number nil}})
    (-> (util/route-path request :staff.reward/view {:id (:id reward)})
        (response/found))))


(def routes
  [""
   ["/reward"
    {:name       :staff.reward/update
     :get        #'card-form
     :post       #'update-card
     :parameters {:form :staff.reward/form}}]
   ["/rewards"
    {:name       :staff.rewards/index
     :get        #'index
     :parameters {:query :admin.reward.index/query}}]
   ["/rewards/:id"
    {:name       :staff.reward/view
     :get        #'view
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["rewards/:id/repay"
    {:name       :staff.reward/repay
     :post       #'repay
     :parameters {:path   {:id :jarima.spec/uuid}}}]])
