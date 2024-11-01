(ns jarima.kash
  (:require
    [jarima.config :refer [env]]
    [clj-http.client :as client]
    [jarima.util :as util]))


(defn pay [tenant_pid service_id amount client]
  (->> {:as              :json
        :content-type    :json
        :decompress-body false
        :headers         {"Accept-Encoding" "identity"}
        :query-params    {:key (:kash-key env)}
        :form-params     {:tenant_pid tenant_pid :service_id service_id :amount amount :client client}}
       (client/post (str (:kash-url env) "/pay"))
       (:body)))


(defn balance []
  (->> {:as              :json
        :query-params    {:key (:kash-key env)}}
       (client/get (str (:kash-url env) "/me"))
       (:body)
       (:deposit)))


(defn card [number]
  (->> {:as             :json
        :query-params   {:key (:kash-key env)}
        :form-params    {:card number}
        :conn-timeout   1000
        :socket-timeout 5000}
       (client/post (str (:kash-url env) "/validate-card"))
       (:body)))


(comment
  (balance)
  (card "8600490499272235")
  (card "8600490499270000"))
