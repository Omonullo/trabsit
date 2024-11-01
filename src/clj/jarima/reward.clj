(ns jarima.reward
  (:import [org.joda.time DateTime])
  (:require [chime]
            [clojure.string :as str]
            [jarima.util :as util]
            [jarima.uzcard :as uzcard]
            [honeysql.core :as sql]
            [jarima.db.core :as db]
            [jarima.db.query :as q]
            [clj-time.core :as time]
            [jarima.config :refer [env]]
            [medley.core :refer [uuid random-uuid]]
            [jarima.payment :refer [pay]]
            [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [jarima.http-log :as http-log]
            [clojure.tools.logging :as log]
            [conman.core :refer [with-transaction]]
            [clj-time.periodic :refer [periodic-seq]]))


(defn reward-type
  [params]
  (cond
    (:phone params) "phone"
    (:card params) "card"
    (:fund params) "fund"
    (:bank params) "bank"
    (:no-reward params) "no-reward"
    :else (throw (ex-info "Unknown type" params))))


(defn assoc-citizen
  [reward]
  (->> (q/select-citizen {:id [(:citizen_id reward)]})
       (db/query-first)
       (assoc reward :citizen)))


(defn handle-non-bank-and-non-card-reward
  [id]
  (with-transaction [db/*db*]
    (when-let [reward
               (-> {:select    [:reward.* :report.citizen_id]
                    :from      [:reward]
                    :left-join [:offense [:= :offense.reward_id :reward.id]
                                :report [:= :report.id :offense.report_id]]
                    :where     [:and
                                [:= :reward.id id]
                                [:not [:in :reward.type (into #{"bank" "card"} (:disabled-rewards env))]]
                                [:or [:= "created" :reward.status] [:and [:= "failed" :reward.status] [:= nil :reward.failure_message]]]]}
                   (q/for-update :reward :skip-locked true)
                   (db/query assoc-citizen)
                   (first))]
      (log/info "Handling reward" (:id reward) (:params reward) "...")
      (binding [http-log/*logs* (atom [])]
        (let [time (util/now)]
          (try
            (let [result (http-log/capture-logs #(pay reward))]
              (if (= (:status result) "awaiting")
                ; if the payment is still awaiting, we need to wait for it to complete, poll it next iteration
                (do
                  (->> {:status             "created"
                        :transaction_number (-> result :ext_id)
                        :payment_result     (db/raw-value result)}
                       (q/update-reward (:id reward))
                       (db/query))
                  (log/info "Reward is awaiting" (:id reward)))
                ; otherwise, there were no errors, then the payment is paid or ignored
                (do (->> {:status          (if (= (:status result) "ignored")
                                             "ignored"
                                             "paid")
                          :failure_message nil
                          :failure_time    nil
                          :pay_time        time
                          :payment_result  (db/raw-value result)}
                         (q/update-reward (:id reward))
                         (db/query))
                    (log/info "Reward pay succeeded" (:id reward)))))
            (catch Throwable t
              (log/warn "Reward forward failed" (:id reward) (ex-message t))
              (->> {:status             "failed"
                    :failure_time       time
                    :failure_message    (ex-message t)
                    :transaction_number nil}
                   (q/update-reward (:id reward))
                   (db/query)))
            (finally
              (->> {:payment_log (sql/call
                                   :concat_ws "\n\n\n"
                                   :payment_log (str
                                                  (util/unparse-local-date-time time) "\n"
                                                  (apply str @http-log/*logs*)))}
                   (q/update-reward (:id reward))
                   (db/query)))))))))


(defn run [_]
  (-> {:select   [:id]
       :from     [:reward]
       :limit    100
       :where    [:and
                  [:not [:in :reward.type (into #{"bank" "card"} (:disabled-rewards env))]]
                  [:or [:= "created" :reward.status] [:and [:= "failed" :reward.status] [:= nil :reward.failure_message]]]]
       :order-by [[(sql/call :case
                     [:= :type "phone"] 0
                     [:= :type "fund"] 1 :else 2)]
                  :reward.create_time]}
      (db/query (comp handle-non-bank-and-non-card-reward :id))
      (dorun))
  true)


(defstate ticker
  :stop (async/close! ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (periodic-seq (time/seconds 60)))
           {:ch (async/chan (async/sliding-buffer 1))}))


(defstate runner
  :start (async/pipeline-blocking
           1 (async/chan (async/sliding-buffer 1)) (map #'run) ticker))


(defn consolidated-card-rewards
  ([] (consolidated-card-rewards nil))
  ([only-ids]
   {:select    [[(sql/call :coalesce :s.card :c.card) :card]
                [(sql/call :case [:= nil :s.card] :c.id :else nil) :citizen_id]
                [(sql/call :case [:= nil :s.card] nil :else :s.id) :staff_id]
                [(sql/call :case [:= "created" :w.status] :w.transaction_number :else nil) :transaction_number]
                [(sql/call :sum :w.amount) :amount]
                [(sql/call :count :w.id) :reward_count]
                [(sql/call :jsonb_agg :w.id) :reward_ids]]
    :from      [[:reward :w]]
    :left-join [[:offense :o] [:= :w.id :o.reward_id]
                [:report :r] [:= :o.report_id :r.id]
                [:citizen :c] [:= :r.citizen_id :c.id]
                [:staff :s] [:= :w.staff_id :s.id]]
    :where     [:and
                [:!= nil (sql/call :coalesce :s.card :c.card)]
                [:= :w.type "card"]
                [:or [:= "created" :w.status] [:and [:= "failed" :w.status] [:= nil :w.failure_message]]]
                (when only-ids [:in :w.id (map uuid only-ids)])]
    :group-by  [:1 :2 :3 :4]
    :order-by  [:5]}))


(defn lock-rewards
  [ids]
  (-> {:select [:true]
       :from   [:reward]
       :where  [:in :id (map uuid ids)]}
      (q/for-update :reward :skip-locked true)
      (db/execute)))

(defn handle-consolidated-card-reward
  [ids]
  (with-transaction [db/*db*]
    (lock-rewards ids)
    (when-let [[{:keys [card citizen_id staff_id transaction_number amount reward_count reward_ids]}]
               (seq (db/query (consolidated-card-rewards ids)))]
      (let [time (util/now)
            payment (-> {:insert-into :card_payment
                         :values      [{:id           (random-uuid)
                                        :card         card
                                        :citizen_id   citizen_id
                                        :staff_id     staff_id
                                        :reward_count reward_count
                                        :reward_ids   (db/raw-value (vec reward_ids))
                                        :amount       amount
                                        :status       "created"
                                        :create_time  (util/now)}]
                         :returning   [:*]}
                        (db/query-first))]
        (log/info "Handling payment" (:number payment) "for card" card "with consolidated amount" amount "...")
        (binding [http-log/*logs* (atom [])]
          (try
            (let [result
                  (http-log/capture-logs
                    (fn []
                      (if (some? transaction_number)
                        ; if there is a transaction_number, then we are in the middle of the payment
                        ; if we get error here the associated rewards are marked as failed and will be handled in the next iteration
                        (uzcard/payment-response (uzcard/state transaction_number))

                        ; if there is no transaction_number, then we are in start stage
                        (let [card-type (uzcard/card-type card)
                              card-info (when (-> card-type :is_uzcard boolean) (uzcard/card-info card))
                              is-card-valid (= 0 (:state card-info))]
                          (if is-card-valid
                            (let [check (uzcard/check card (* amount 100) (random-uuid))]
                              (uzcard/payment-response
                                (try
                                  ;; beyond this point, all errors must be treated as awaiting payments
                                  (uzcard/pay (:ext_id check))
                                  (catch Throwable t
                                    (if (some-> (ex-message t) (str/starts-with? "Uzcard pay failed:"))
                                      (throw t)
                                      (merge check {:state       30
                                                    :description (str "Error occurred during pay request: " (ex-message t)
                                                                      "The check is explicitly marked as status 30 to recheck it on next iteration")}))))))
                            (throw (ex-info (str (if (not (-> card-type :is_uzcard boolean))
                                                   "Uzcard: Non uzcard card not supported"
                                                   "Uzcard: Card is disabled"))
                                            {:card-type card-type
                                             :card-info card-info})))))))
                  patch (merge {:transaction_number (:ext_id result)
                                :failure_message    nil
                                :failure_time       nil
                                :payment_result     (db/raw-value result)}
                               (if (= (:status result) "awaiting")
                                 ; if the payment is still awaiting, we need to wait for it to complete, poll it next iteration
                                 {:status "created"}
                                 ; otherwise, there were no errors, then the payment is paid
                                 {:status "paid" :pay_time time}))]

              (->> {:update :reward
                    :set    (assoc patch :params (db/raw-value {:card card}))
                    :where  [:in :id (map uuid reward_ids)]}
                   (db/execute))
              (->> {:update :card_payment
                    :set    patch
                    :where  [:= :id (:id payment)]}
                   (db/execute))
              (log/info "Payment" (:number payment) "is" (:status result)))
            (catch Throwable t
              (let [patch {:status             "failed"
                           :failure_time       time
                           :failure_message    (ex-message t)
                           :transaction_number nil}]
                (->> {:update :reward
                      :set    patch
                      :where  [:in :id (map uuid reward_ids)]}
                     (db/execute))
                (->> {:update :card_payment
                      :set    patch
                      :where  [:= :id (:id payment)]}
                     (db/execute))
                (log/warn "Payment" (:number payment) "is failed:" (ex-message t))))
            (finally
              (let [patch {:payment_log (sql/call
                                          :concat_ws "\n\n\n"
                                          :payment_log (str
                                                         (util/unparse-local-date-time time) "\n"
                                                         (apply str @http-log/*logs*)))}]
                (->> {:update :reward
                      :set    patch
                      :where  [:in :id (map uuid reward_ids)]}
                     (db/execute))
                (->> {:update :card_payment
                      :set    patch
                      :where  [:= :id (:id payment)]}
                     (db/execute))))))))))


(defstate consolidated-card-reward-runner
  :stop (consolidated-card-reward-runner)
  :start (chime/chime-at (periodic-seq (-> (DateTime/now (time/default-time-zone))
                                           (.withTime 22 0 0 0))
                                       (-> 1 time/days))
                         (fn [_]
                           (-> (consolidated-card-rewards)
                               (db/query (comp handle-consolidated-card-reward :reward_ids))
                               (dorun)))))

(comment
  (-> (consolidated-card-rewards)
      (db/query (comp handle-consolidated-card-reward :reward_ids))
      (dorun))

  (-> (consolidated-card-rewards)
      (db/query))

  (handle-consolidated-card-reward ["552cc404-9d85-4b21-9e13-8f75fd3557f2"]))
