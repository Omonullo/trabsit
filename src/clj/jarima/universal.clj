(ns jarima.universal
  (:require [clj-http.client :as http]
            [jarima.config :refer [env]]
            [clojure.core.memoize :as memo]))



(def get-token
  (memo/ttl
    (fn []
      (let [{:keys [status result] :as body}
            (-> "https://core.unired.uz/api/v1/unired"
                (http/post
                  {:json-opts        {:pretty true}
                   :content-type     :json
                   :as               :json
                   :form-params
                                     {:id     "1"
                                      :method "login"
                                      :params {:login    (:universal-login env)
                                               :password (:universal-password env)}}
                   :throw-exceptions false})
                (:body))]
        (if status
          (:access_token result)
          (throw (ex-info "Universal login failed" body)))))
    {} :ttl/threshold (* 1/2 60 60 1000)))


(defn balance []
  (let [{:keys [status result] :as body}
        (-> "https://core.unired.uz/api/v1/unired"
            (http/post
              {:json-opts    {:pretty true}
               :content-type :json
               :as           :json
               :headers      {:Unisoft-Authorization (:universal-token env)}
               :form-params  {:jsonrpc "2.0"
                              :id      "1"
                              :method  "card.register"
                              :params  {:card_number (:universal-card-number env)
                                        :expire      (:universal-card-expiry env)}}})
            (:body))]
    (if status
      (-> result :balance bigint (/ 100))
      (throw (ex-info "Universal balance failed" body)))))


(comment
  (balance))
