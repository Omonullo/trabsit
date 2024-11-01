(ns jarima.handler.admin.transfer
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid? send])
  (:require [jarima.db.core :as db]
            [jarima.db.query :as query]
            [medley.core :refer :all]
            [conman.core :refer [with-transaction]]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]
            [jarima.util :as util]
            [jarima.spec :as spec]
            [honeysql.core :as sql]))


(defn index
  [_]
  (layout/render
    "admin/transfer/list.html"
    {:transfers (-> (query/select-transfer nil)
                    (db/query))}))


(defn index
  [{{query :query} :parameters :as request}]
  (layout/render
    "admin/transfer/list.html"
    (-> (util/paged-query
          (partial query/select-transfer
            (-> query
                (util/vectorify-vals (complement #{:bank_account :create_time}))))
          (:page query)
          [:%sum.amount :amount])
        (assoc :request request)
        (assoc :statuses (keys spec/transfer-statuses)))))


(defn view
  [request]
  (let [transfer
        (-> (query/select-transfer {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not transfer
      (layout/error-page!
        {:status 404
         :title  (t "Вопрос не найден")}))
    (layout/render
      "admin/transfer/view.html"
      {:transfer transfer
       :rewards  (-> {:transfer_id [(:id transfer)]}
                     (query/select-reward)
                     (db/query))})))


(defn send
  [request]
  (conman.core/with-transaction [db/*db*]
    (let [now (util/now)
          transfer
          (-> (query/select-transfer
                {:id     [(-> request :parameters :path :id)]
                 :status ["created"]})
              (db/query-first))]
      (when-not transfer
        (layout/error-page!
          {:status 404
           :title  (t "Перевод не найден")}))
      (-> (query/update-transfer
            (:id transfer)
            {:status "sent" :send_time (util/now)})
          (db/query))
      (-> {:update :reward
           :where  [:uuid-in :transfer_id [(:id transfer)]]
           :set    {:status      "paid"
                    :pay_time    now}}
          (db/execute))
      (-> (util/route-path request :admin.transfer/view {:id (:id transfer)})
          (response/found)))))


(defn create
  [request]
  (conman.core/with-transaction [db/*db*]
    (let [now (util/now)]
      (doseq [{:keys [bank_account amount reward_ids]}
              (-> {:select   [(sql/raw "params ->> 'bank' bank_account")
                              (sql/raw "sum(amount) amount")
                              (sql/raw "jsonb_agg(id) reward_ids")]
                   :from     [:reward]
                   :where    [:and
                              [:= :type "bank"]
                              [:= :status "created"]
                              [:= :transfer_id nil]]
                   :group-by [:1]}
                  (db/query))]
        (let [id (random-uuid)]
          (-> {:id           id
               :bank_account bank_account
               :amount       amount
               :status       "created"
               :create_time  now}
              (query/insert-transfer)
              (db/execute))
          (doseq [partial-reward-ids (partition-all 1000 reward_ids)]
            (-> {:update :reward
                 :where  [:in :id (mapv uuid partial-reward-ids)]
                 :set    {:transfer_id id}}
                (db/execute)))))))
  (-> (util/route-path request :admin.transfer/index)
      (response/found)))


(def routes
  ["/transfers"
   [""
    {:name       :admin.transfer/index
     :get        #'index
     :parameters {:query :admin.transfer.index/query}}]

   ["/create"
    {:name :admin.transfer/create
     :post #'create}]

   ["/:id/view"
    {:name       :admin.transfer/view
     :get        #'view
     :parameters {:path {:id :jarima.spec/uuid}}}]

   ["/:id/send"
    {:name       :admin.transfer/send
     :post       #'send
     :parameters {:path {:id :jarima.spec/uuid}}}]])
