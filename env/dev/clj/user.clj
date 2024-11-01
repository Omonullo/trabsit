(ns user
  (:require
    [jarima.core]
    [jarima.http]
    [jarima.nrepl]
    [jarima.db.core]
    [mount.core :as mount]
    [clojure.spec.alpha :as s]
    [expound.alpha :as expound]
    [jarima.config :refer [env]]
    [luminus-migrations.core :as migrations]
    [clojure.set :as set]
    [jarima.db.core :as db]
    [jarima.db.query :as q]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn start []
  (mount/start-without #'jarima.nrepl/server))

(defn stop []
  (mount/stop-except #'jarima.nrepl/server))

(defn restart []
  (stop)
  (start))

(defn migrate []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration [name]
  (migrations/create name (select-keys env [:database-url])))

#_(start)


(defn reconcile-locales [dictionary]
  (let [uz_la (-> dictionary :translations :uz_la)
        uz_cy (-> dictionary :translations :uz_cy)]
    (println "Missing translations for uz_la")
    (doseq [ts (set/difference (set (keys uz_la))
                               (set (keys uz_cy)))]
      (println ts))
    (println "Missing translations for uz_cy")
    (doseq [ts (set/difference (set (keys uz_cy))
                               (set (keys uz_la)))]
      (println ts))))


#_(defn reset-db []
    (migrations/migrate ["reset"] (select-keys env [:database-url])))


(defn fix-revisions []
  (conman.core/with-transaction [db/*db*]
    (for [revision (-> {:select [:*]
                        :from   [:revision]}
                       (db/query #(update % :data read-string)))]
      (let [offenses (-> revision :data :offenses)
            persist-offenses (if (and
                                   (not (sequential? offenses))
                                   (contains? offenses :lock))
                               (-> offenses :lock first)
                               offenses)]
        (-> (q/update-revision (:report_id revision)
                               (:invalidate_time revision)
                               (update revision
                                       :data (fn [data]
                                               (-> data
                                                   (assoc :offenses persist-offenses)
                                                   (prn-str)))))
            (db/query))))))
