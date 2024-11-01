(ns jarima.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cprop.source :as source]
            [duratom.core :refer [duratom]]
            [cprop.core :refer [load-config]]
            [mount.core :refer [args defstate]]
            [camel-snake-kebab.core :refer [->SCREAMING_SNAKE_CASE]]
            [clojure.string :as str]))

(defstate env
  :start
  (load-config
    :merge
    [(args)
     (source/from-system-props)
     (source/from-env)]))


(defstate mwage
  :start (duratom :postgres-db
           :db-config (:database-url env)
           :table-name "config"
           :row-id 0
           :init 202730))


(defstate dictionary
  :start
  (-> "dictionary.edn" io/resource slurp edn/read-string))


(defn- -generate-dokku-config [m]
  (if (map? m)
    (->> m
         (map
           (fn [[k v]]
             (map (fn [value]
                    (str (->SCREAMING_SNAKE_CASE (name k))
                         "__"
                         value))
                  (-generate-dokku-config v))))
         (apply concat))
    [m]))


(defn generate-dokku-config
  ([m]
   (generate-dokku-config " " m))
  ([sep m]
   (->> (-generate-dokku-config m)
        (map
          (fn [s]
            (let [ks (str/split s #"__")]
              (str/join
                "="
                [(str/join "__" (butlast ks))
                 (last ks)]))))
        (str/join sep))))

