(ns jarima.handler.service
  (:require
    [ring.util.http-response :as response]
    [jarima.validation :as v]
    [buddy.auth :refer [authenticated? throw-unauthorized]]
    [jarima.sms :as sms]))


(defn sms
  [request]
  (let [{params :params identity :identity} request
        {:keys [message, phone]} params]
    (if (authenticated? request)
      (if-let [errors (v/validate-sms {:message message :phone phone})]
        (response/bad-request errors)
        (do
          (sms/send phone message)
          (response/ok {:status "ok" :result "SMS sent"})))
      (throw-unauthorized))))


(def routes
  [["/sms"
    {:name :service/sms
     :post #'sms}]])


