(ns jarima.db.core
  (:require
    [java-time :as jt]
    [honeysql.core :as sql]
    [conman.core :as conman]
    [honeysql-postgres.format]
    [clojure.java.jdbc :as jdbc]
    [jarima.config :refer [env]]
    [mount.core :refer [defstate]]
    [cheshire.generate]
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.tools.logging :as log]
    [cuerdas.core :as str])
  (:import [java.sql Array]
           [clojure.lang IDeref]
           [clojure.lang IPersistentMap]
           [org.postgresql.util PGobject]
           [clojure.lang IPersistentVector]
           (java.time Instant Duration)))


(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url      (env :database-url)
                           :max-pool-size 40
                           :min-idle      10})
  :stop (conman/disconnect! *db*))


(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _2 _3]
    (.toLocalDateTime v))
  java.sql.Date
  (result-set-read-column [v _2 _3]
    (.toLocalDate v))
  java.sql.Time
  (result-set-read-column [v _2 _3]
    (.toLocalTime v))
  Array
  (result-set-read-column [v _ _] (vec (.getArray v)))
  PGobject
  (result-set-read-column [object _metadata _index]
    (let [type  (.getType object)
          value (.getValue object)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))


(defn to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (generate-string value))))


(extend-type clojure.lang.IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))


(extend-protocol jdbc/ISQLValue
  java.util.Date
  (sql-value [v]
    (java.sql.Timestamp. (.getTime v)))
  java.time.LocalTime
  (sql-value [v]
    (jt/sql-time v))
  java.time.LocalDate
  (sql-value [v]
    (jt/sql-date v))
  java.time.LocalDateTime
  (sql-value [v]
    (jt/sql-timestamp v))
  java.time.ZonedDateTime
  (sql-value [v]
    (jt/sql-timestamp v))
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value))
  IDeref
  (sql-value [value] (jdbc/sql-value @value)))


(def ^:dynamic *debug* false)


(defmacro with-debug
  [& body]
  `(binding [*debug* true]
     ~@body))


(defn query
  ([sql-map]
   (query sql-map nil))
  ([sql-map row-fn]
   (let [sql-params (sql/format sql-map)]
     (when *debug*
       (log/debug sql-params))
     (try
       (let [start-time (Instant/now)
             result (doall (jdbc/query *db* sql-params (when row-fn {:row-fn row-fn})))]
         (try
           (let [duration (Duration/between start-time (Instant/now))]
             (when (> (.toMillis duration) 1000)
               (log/info
                 (str (->
                        duration
                        (.toString)
                        (.substring 2)
                        (.replaceAll "(\\d[HMS])(?!$)", "$1 ")
                        (.toLowerCase))
                      " took "
                      (first sql-params)))))
           (catch Exception error
             (log/error error)))
         result)
       (catch Throwable t
         (log/warn (ex-message t) sql-params)
         (throw t))))))


(defn query-first
  ([sql-map]
   (query-first sql-map nil))
  ([sql-map row-fn]
   (first (query sql-map row-fn))))


(defn execute [sql-map]
  (jdbc/execute!
    *db*
    (let [sql-params (sql/format sql-map)]
      (when *debug* (log/debug sql-params))
      sql-params)))


(defn raw-value
  [value]
  (reify IDeref
    (deref [_] value)))


(extend-protocol cheshire.generate/JSONable
  java.time.LocalDateTime
  (to-json [v gen]
    (.writeString gen (.toString v))))


(defn delete
  [table id]
  (try
    (first (jdbc/delete! *db* table ["id = ?" id]))
    (catch Exception e
      (if (str/includes? (ex-message e) "is still referenced from table")
        0
        (throw e)))))
