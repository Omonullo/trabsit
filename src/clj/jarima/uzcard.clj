(ns jarima.uzcard
  (:require [buddy.hashers]
            [clj-http.client :as http]
            [clojure.core.memoize :as memo]
            [clojure.tools.logging :as log]
            [jarima.config :refer [env]]
            [jarima.http-log :as http-log]
            [jarima.util :as util]))


(def get-token
  (memo/ttl
    (fn []
      (log/info "Getting access token...")
      (-> (-> env :uzcard :url)
          (str "/api/v1/login/")
          (http/post {:content-type :json
                      :as           :json
                      :form-params  {:id      1,
                                     :jsonrpc "2.0",
                                     :method  "login",
                                     :params  {:username (-> env :uzcard :username),
                                               :password (-> env :uzcard :password)}}})
          (:body)
          (:data)
          (:access_token)
          (doto (assert "Failed to get access token"))))
    {} :ttl/threshold (-> (* 5) (* 60) (* 1000))))


(defn request [method params]
  (-> (-> env :uzcard :url)
      (str "/api/v1/unired/")
      (http/post {:content-type   :json
                  :as             :json
                  :headers        {:Authorization (str "Bearer " (get-token))}
                  :conn-timeout   10000
                  :socket-timeout 60000
                  :form-params    {:id      1,
                                   :jsonrpc "2.0",
                                   :method  method,
                                   :params  params}})
      (:body)))


(defn check
  [card amount transaction-number]
  (let [response (request
                   "transfer.credit.create.mvd"
                   {:ext_id      transaction-number,
                    :card_number card,
                    :amount      amount,
                    :merchant_id (-> env :uzcard :merchant-id),
                    :terminal_id (-> env :uzcard :terminal-id)})]
    (if (:status response)
      (:result response)
      (let [error-message (or (:message (:error response)) "unknown error")]
        (throw
          (ex-info
            (str "Uzcard check failed: " error-message)
            (:error response)))))))

(defn pay [transaction-number]
  (let [response (request "transfer.credit.confirm" {:ext_id transaction-number})]
    (if (:status response)
      (:result response)
      (let [error-message (or (:message (:error response)) "unknown error")]
        (throw
          (ex-info (str "Uzcard pay failed: " error-message)
                   (:error response)))))))


(defn state [transaction-number]
  (let [response (request "transfer.credit.state" {:ext_id transaction-number})]
    (if (:status response)
      (:result response)
      (let [error-message (or (:message (:error response)) "unknown error")]
        (throw
          (ex-info (str "Uzcard status failed: " error-message)
                   (:error response)))))))


(defn card-type [card]
  (let [response (request "bin.check.card" {:card_number card})]
    (if (:status response)
      (:result response)
      (let [error-message (or (:message (:error response)) "unknown error")]
        (throw
          (ex-info (str "Uzcard status failed: " error-message)
                   (:error response)))))))

(defn card-info [card]
  (let [response (request "card.info" {:card_number card})]
    (if (empty? (:result response))
      (:error response)
      (:result response))))


(defn payment-response [result]
  (let [state (-> result :state)]
    (cond
      (= state 4)
      (assoc result :status "success")

      (some? (#{30} state))
      (assoc result :status "awaiting")

      (some? (#{0 2 3 10 11 12 16} state))
      (throw (ex-info (str "Uzcard: Error status for transaction: " state) {:response result}))

      :else (throw (ex-info (str "Uzcard: Unknown uzcard state: " state) {:response result})))))

(comment
  "ext_id already exist"
  (let [{:keys [ext_id state amount currency account merchant]} (check "8600490403208994" 1000 "12345")]))

(comment (check "8600490403208994" 1000 "12345"))

(comment (card-info "8600490403208994"))

(comment (boolean (:is_uzcard (card-type "8600490403208994"))))

(comment (state "12345"))


(def my-ip "93.171.190.190")

(defn balance
  []
  (->
    (util/mute
      (request
        "transfer.get.account.details"
        {:id 1}))
    (:result)
    (:responseBody)
    (:saldo)
    (or 0)
    (str)
    (BigInteger.)
    (/ 100)))


#_(defn send-sms-code [login password]
    (binding [http-log/*logs* (atom [])]
      (try
        (http-log/capture-logs
          #(http/post "https://myuzcard.uz/api/v2/Auth/loginUser"
                      {:as               :json
                       :content-type     :json
                       :basic-auth       [login password]
                       :form-params      {"deviceId"      device-id
                                          "displayName"   display-name
                                          "ip"            my-ip
                                          "otp"           ""
                                          "imei"          ""
                                          "firebaseToken" ""
                                          "deviceType"    1}
                       :throw-exceptions false}))
        (catch Exception _
          (println
            (str
              (util/unparse-local-date-time (util/now)) "\n"
              (apply str @http-log/*logs*)))))))



#_(defn confirm-sms-code [login password code]
    (binding [http-log/*logs* (atom [])]
      (try
        (http-log/capture-logs
          #(http/post "https://myuzcard.uz/api/v2/Auth/loginUserWithOtp"
                      {:as               :json
                       :content-type     :json
                       :basic-auth       [login password]
                       :form-params      {"deviceId"      device-id
                                          "displayName"   display-name
                                          "ip"            my-ip
                                          "otp"           code
                                          "isTrusted"     "true",
                                          "imei"          ""
                                          "firebaseToken" ""
                                          "deviceType"    1}
                       :throw-exceptions false}))
        (catch Exception _
          (println
            (str
              (util/unparse-local-date-time (util/now)) "\n"
              (apply str @http-log/*logs*)))))))


(comment
  (balance)
  (check (util/now) (str (gensym)) "8600490473512051")
  (pay (util/now) (str (gensym)) "8600490473512051" "100.00")
  (util/mute (status (util/now) "20200211" "16329_5182"))
  (util/mute (status (util/now) "20200318" "79030_531"))
  (get-in (util/mute (status (util/now) "20200101" "55731_1340"))
          [:attrs :ERR_TEXT])

  {:tag     :RESPONSE,
   :attrs   {:Stamp "Вт фев 18 15:09:01 UZT 2020", :RE "0", :ERR_TEXT "ERR"},
   :content [{:tag :TransacID, :attrs {}, :content ["8354_6701"]}
             {:tag :TrnAmount, :attrs {}, :content ["11150"]}
             {:tag :PaymentInfo, :attrs {}, :content ["transfer"]}
             {:tag :ErrorPayInfo, :attrs {}, :content ["ERR"]}]})
