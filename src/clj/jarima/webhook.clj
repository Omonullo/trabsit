(ns jarima.webhook
  (:import [org.joda.time DateTime])
  (:require [chime]
            [honeysql.helpers :refer [merge-where]]
            [jarima.db.core :as db]
            [jarima.db.query :as q]
            [clj-time.core :as time]
            [medley.core :refer [uuid]]
            [clojure.core.async :as async]
            [cprop.core :refer [load-config]]
            [mount.core :refer [args defstate]]
            [selmer.parser :refer [render-file]]
            [conman.core :refer [with-transaction]]
            [jarima.config :refer [dictionary env]]
            [clj-time.periodic :refer [periodic-seq]]
            [jarima.handler.api.report :as api.report]
            [clj-http.client :as client]
            [jarima.util :as util]
            [clojure.tools.logging :as log]
            [medley.core :refer [assoc-some]]
            [cuerdas.core :as str]))

(defn ->webhook-report [report]
  (api.report/report-response report))


(defn ->webhook-offense [offense]
  (let [reward-status (-> (q/select-reward {:id [(:reward_id offense)]})
                          (db/query-first)
                          (get :status "absent"))]
    (-> offense
        (api.report/offense-response)
        (assoc :reward_status reward-status))))



(defn block-row [table row-id]
  (-> {:select [:id]
       :from   [table]
       :where  [:uuid-in :id [row-id]]}
      (q/for-update)
      (db/query-first :id)))


(defn run [_]
  (with-transaction [db/*db*]
    (log/info "Running webhooks cron")
    (let [offence-future
          (future (doseq [offense-id (-> {:select [:id]
                                          :from   [:offense]}
                                         (merge-where [:and
                                                       [:!= nil :creator_client_id]
                                                       [:= nil :creator_client_notified_at]])
                                         (db/query :id))]
                    (block-row :offense offense-id)
                    (let [offense (-> {:id [offense-id]}
                                      (q/select-offense)
                                      (db/query-first))
                          oauth-client (-> {:id      [(:creator_client_id offense)]
                                            :enabled true}
                                           (q/select-oauth-client)
                                           (db/query-first))]
                      (try
                        (when
                          (and
                            (:offense_status_webhook oauth-client)
                            (= 200 (->> {:content-type   :json
                                         :headers        {"Accept-Encoding" "identity"}
                                         :basic-auth     [(:webhook_login oauth-client) (:webhook_password oauth-client)]
                                         :form-params    (->webhook-offense offense)
                                         :conn-timeout   20000
                                         :socket-timeout 30000}
                                        (client/post (:offense_status_webhook oauth-client))
                                        (:status))))
                          (-> (:id offense)
                              (q/update-offense {:creator_client_notified_at (util/now)})
                              (db/query)))
                        (catch Exception e
                          (log/error e (str/format "Webhook request failed, Client id = %s" (:id oauth-client))))))))
          report-future
          (future (doseq [report-id (-> {:select [:id]
                                         :from   [:report]}
                                        (merge-where [:and
                                                      [:!= nil :creator_client_id]
                                                      [:= nil :creator_client_notified_at]])
                                        (db/query :id))]
                    (block-row :report report-id)
                    (let [report (-> {:id [report-id]}
                                     (q/select-report)
                                     (db/query-first))
                          oauth-client (-> {:id     [(:creator_client_id report)]
                                            :enable true}
                                           (q/select-oauth-client)
                                           (db/query-first))]
                      (try
                        (when
                          (and
                            (:report_status_webhook oauth-client)
                            (= 200 (->> {:content-type   :json
                                         :form-params    (->webhook-report report)
                                         :headers        {"Accept-Encoding" "identity"}
                                         :basic-auth     [(:webhook_login oauth-client) (:webhook_password oauth-client)]
                                         :conn-timeout   20000
                                         :socket-timeout 30000}
                                        (client/post (:report_status_webhook oauth-client))
                                        (:status))))
                          (-> (:id report)
                              (q/update-report {:creator_client_notified_at (util/now)})
                              (db/query)))
                        (catch Exception e
                          (log/error e (str/format "Webhook request failed, Client id = %s" (:id oauth-client))))))))]
      @offence-future
      @report-future))
  true)

(comment
  (with-transaction [db/*db*]
    (-> {:select [:id]
         :from   [:report]}
        (merge-where [:uuid-in :id [#uuid"08f77633-6f34-4f32-9bce-b6402362e859"]])
        (q/for-update)
        (db/query))
    (Thread/sleep 20000)))



(defstate ticker
  :stop (async/close! ticker)
  :start (chime/chime-ch
           (-> (DateTime/now (time/default-time-zone))
               (periodic-seq (time/seconds 60)))
           {:ch (async/chan (async/sliding-buffer 1))}))


(defstate runner
  :start (async/pipeline-blocking
           1 (async/chan (async/sliding-buffer 1)) (map #'run) ticker))
