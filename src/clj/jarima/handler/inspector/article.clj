(ns jarima.handler.inspector.article
  (:require [jarima.db.core :as db]
            [jarima.layout :as layout]
            [jarima.db.query :as query]))


(defn index
  [_]
  (layout/render
    "inspector/article/list.html"
    {:articles
     (-> (query/select-article nil)
         (db/query))}))


(def routes
  ["/articles"
   [""
    {:name :inspector.article/index
     :get  #'index}]])
