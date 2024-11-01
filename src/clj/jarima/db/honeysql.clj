(ns jarima.db.honeysql
  (:refer-clojure :exclude [update select format partition-by])
  (:require [cuerdas.core :as str]
            [honeysql.core :as sql]
            [honeysql.format :refer :all]
            [honeysql.helpers :refer :all]
            [honeysql-postgres.format :refer :all]
            [honeysql-postgres.helpers :refer :all]
            [honeysql-postgres.util :refer [comma-join-args]]))


(defn empty-uuid-seq
  [uuid-seq]
  (if (seq uuid-seq)
    uuid-seq
    [#uuid "00000000-0000-0000-0000-000000000000"]))


(defmethod fn-handler "@@"
  [_ a b & more]
  (if (seq more)
    (expand-binary-ops "@@" a b more)
    (str (to-sql a) " @@ " (to-sql b))))


(defmethod fn-handler "uuid-in"
  [_ column uuid-seq]
  (let [clause
        (let [uuid-seq (filter some? uuid-seq)]
          (str
            (to-sql column)
            " in "
            (to-sql (empty-uuid-seq uuid-seq))))]
    (if (some nil? uuid-seq)
      (str "(" (to-sql column) " is null or " clause ")")
      clause)))


(defmethod fn-handler "str-in"
  [_ column str-seq]
  (let [clause
        (let [str-seq (filter some? str-seq)]
          (str
            (to-sql column)
            " in "
            (to-sql (if (seq str-seq) str-seq [""]))))]
    (if (some nil? str-seq)
      (str "(" (to-sql column) " is null or " clause ")")
      clause)))



(defmethod fn-handler "int-in"
  [_ column int-seq]
  (let [clause
        (let [int-seq (filter some? int-seq)]
          (str
            (to-sql column)
            " in "
            (to-sql (if (seq int-seq) int-seq [0]))))]
    (if (some nil? int-seq)
      (str "(" (to-sql column) " is null or " clause ")")
      clause)))


(defmethod fn-handler "re-matches"
  [_ column regex]
  (str "(" (to-sql column) " ~* " (to-sql regex) ")"))


(defmethod fn-handler "ilike"
  [_ column search-phrase]
  (str "(" (to-sql column) " ilike " (to-sql search-phrase) ")"))


(defmethod fn-handler "to_tsquery"
  [_ search-phrase]
  (str/format "to_tsquery(%s)"
              (to-sql (some-> (reduce (fn [acc s] (str acc ":*&" s))
                                      (-> search-phrase
                                          (str/replace #"[!:*&|<>\(\)\-\\]+" "")
                                          (str/clean)
                                          (str/split " ")))
                              (not-empty)
                              (str ":*")))))


(defmethod fn-handler "@@"
  [_ column search-phrase]
  (str "("
       (to-sql column)
       " @@ "
       (to-sql (sql/call "to_tsquery" search-phrase))
       ")"))


(defmethod fn-handler "extract"
  [_ part value]
  (str/format "extract(%s from %s)" (name part) (to-sql value)))


(defmethod fn-handler "date-trunc"
  [_ part value]
  (str/format "date_trunc('%s', %s::timestamp)" (name part) (to-sql value)))


(defmethod fn-handler "interval"
  [_ value type]
  (str/format "interval '%s' %s" value type))


(defmethod fn-handler "lower"
  [_ value]
  (str "lower(" (to-sql value) ")"))


(defmethod fn-handler "within"
  [_ column range]
  (format-predicate*
    [:and
     (when (:gt range) [:> column (:gt range)])
     (when (:lt range) [:< column (:lt range)])
     (when (:gte range) [:>= column (:gte range)])
     (when (:lte range) [:<= column (:lte range)])
     (when (contains? range :=) [:= column (:= range)])]))


(defmethod fn-handler "diff-days"
  [_ date-1 date-2]
  (to-sql
    (sql/call :extract :day
      (sql/call :-
        (sql/call :date-trunc :day date-1)
        (sql/call :date-trunc :day date-2)))))


(defn apply-pagination
  [sql-map {:keys [size page] :as pagination}]
  (if (some? pagination)
    (-> sql-map
        (offset (* size (dec page)))
        (limit size))
    sql-map))


(defmethod format-clause :lock [[_ lock] _]
  (let [{:keys [mode wait table skip-locked]} lock
        clause (format-lock-clause mode)]
    (str clause
         (when (some? table) (str " of " (to-sql table)))
         (when (false? wait) " nowait")
         (when (true? skip-locked) " skip locked"))))


(defmethod fn-handler "contains?"
  [_ column array]
  (str (to-sql column) "@> ARRAY["
       (str/join "," (map #(str \' (str/replace %1 #"\'" "''") \') array))
       "]::varchar[]"))


(defmethod fn-handler "to-array"
  [_ array]
  (str "array["
       (->> array
            (map #(str \' (str/replace % #"\'" "''") \'))
            (str/join ","))
       "]"))
