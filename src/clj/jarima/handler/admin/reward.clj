(ns jarima.handler.admin.reward
  (:require
    [jarima.util :as util]
    [jarima.spec :as spec]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [jarima.layout :as layout]
    [jarima.config :refer [dictionary env]]
    [jarima.handler.staff.report :as staff.report]
    [jarima.reward :refer [reward-type]]
    [ring.util.http-response :as response]
    [medley.core :refer [uuid random-uuid map-vals filter-vals assoc-some distinct-by]]
    [clojure.tools.logging :as log]))


(defn index
  [{{query :query} :parameters identity :identity}]
  (layout/render
    "admin/reward/list.html"
    (-> (util/paged-query
          (partial q/select-reward
                   (-> (staff.report/restrict-filter query identity)
                       (util/vectorify-vals (complement #{:create_time :receiver_role}))))
          (:page query)
          [:%sum.amount :amount])
        (assoc :statuses (keys spec/reward-statuses))
        (assoc :citizens (-> {:id [(:citizen_id query)]}
                             (q/select-citizen {:size 1 :page 1})
                             (db/query)))
        (assoc :staff (-> {:id [(:staff_id query)]}
                          (q/select-staff {:size 1 :page 1})
                          (db/query)))
        (assoc :types spec/reward-types)
        (assoc :failure_messages (db/query {:select    [:failure_message]
                                            :modifiers [:distinct]
                                            :from      [:reward]
                                            :where     [:and
                                                        [:!= nil :failure_message]
                                                        [:= "failed" :status]]} :failure_message)))))


(defn view [request]
  (let [reward
        (-> {:id (-> request :parameters :path :id)}
            (staff.report/restrict-filter (:identity request))
            (util/vectorify-vals)
            (q/select-reward)
            (db/query-first
              (fn [reward]
                (-> reward
                    (merge
                      {:report        (-> {:id [(:report_id reward)]}
                                          (q/select-report)
                                          (db/query-first))
                       :offense       (-> {:reward_id [(:id reward)]}
                                          (q/select-offense)
                                          (db/query-first))
                       :citizen       (-> {:id [(:citizen_id reward)]}
                                          (q/select-citizen)
                                          (db/query-first))
                       :staff         (-> {:id [(:staff_id reward)]}
                                          (q/select-staff)
                                          (db/query-first))
                       :organizations (-> {:citizen_id [(:citizen_id reward)]}
                                          (q/select-organization)
                                          (db/query))
                       :transfer      (when-let [id (:transfer_id reward)]
                                        (-> {:id [id]}
                                            (q/select-transfer)
                                            (db/query-first)))})))))]

    (when-not reward
      (layout/error-page!
        {:status 404
         :title  "Вознаграждение не найден"}))
    (layout/render
      "admin/reward/view.html"
      {:reward reward
       :types  spec/reward-types
       :funds  (-> env :payment :fund keys)})))


(defn repay [request]
  (let [reward (-> {:id     (-> request :parameters :path :id)
                    :status "failed"}
                   (staff.report/restrict-filter (:identity request))
                   (util/vectorify-vals)
                   (q/select-reward)
                   (db/query-first))]
    (when-not reward
      (layout/error-page!
        {:status 404
         :title  "Вознаграждение не найден"}))
    (let [params (-> request :parameters :params :params not-empty)]
      (db/execute
        {:update    :reward
         :returning [:*]
         :where     [:and [:!= "paid" :status] [:= :id (:id reward)]]
         :set       (cond-> {:failure_message nil :transaction_number nil}
                            (some? params)
                            (assoc :params (db/raw-value params)
                                   :type (reward-type params)))}))
    (-> (util/route-path request :admin.reward/view {:id (:id reward)})
        (response/found))))


(defn repay-all [request]
  (let [identity (-> request :identity)
        query (-> request :parameters :form)]
    (doseq [reward-id
            (-> (merge
                  (staff.report/restrict-filter query identity)
                  {:status "failed"})
                (util/vectorify-vals (complement #{:create_time :receiver_role}))
                (q/select-reward)
                (assoc :select [:reward.id])
                (db/query :id))]
      (log/info "Resetting reward: " reward-id)
      (-> reward-id
          (q/update-reward {:failure_message nil :transaction_number nil})
          (db/query))))
  (-> (util/route-path request :admin.reward/index)
      (response/found)
      (util/recall-referer request (:base-url env))))


(def routes
  ["/rewards"
   [""
    {:name       :admin.reward/index
     :get        #'index
     :parameters {:query :admin.reward.index/query}}]
   ["/repay"
    {:name       :admin.reward/repay-all
     :post       #'repay-all
     :parameters {:form :admin.reward.index/query}}]
   ["/:id/view"
    {:name       :admin.reward/view
     :get        #'view
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["/:id/repay"
    {:name       :admin.reward/repay
     :post       #'repay
     :parameters {:path   {:id :jarima.spec/uuid}
                  :params :admin.reward.repay/form}}]])
