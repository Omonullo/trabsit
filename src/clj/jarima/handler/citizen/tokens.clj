(ns jarima.handler.citizen.tokens
  (:refer-clojure :exclude [list])
  (:require
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [jarima.layout :as layout :refer [t]]
    [jarima.util :as util]))



(defn list [request]
  (let [user (:identity request)]
    (layout/render
      "oauth/token/list.html"
      {:tokens (-> (q/select-oauth-token
                     {:user_id             [(:id user)]
                      :revoked             false
                      :refresh_expire_time {:gt (util/now)}})
                   (db/query))})))


(def routes
  ["/apps"
   {:name       :citizen.apps/list
    :get        #'list}])
