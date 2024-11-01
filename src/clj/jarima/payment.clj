(ns jarima.payment
  (:require
    [jarima.kash :as kash]
    [jarima.config :refer [env]]))


(defmulti pay
  (fn [{:keys [params]}]
    (first (keys params))))


(defmethod pay :phone
  [{:keys [number params amount]}]
  (let [phone  (subs (:phone params) 3)
        {:keys [service_id client_field]} (get-in env [:payment :phone (subs phone 0 2)])
        result (kash/pay (str (gensym (str number "_")))
                         service_id amount
                         {client_field phone})]
    (when-not (= "success" (:status result))
      (throw (ex-info (:reason result) result)))
    result))


(defmethod pay :fund
  [{:keys [number params amount citizen]}]
  [params amount]
  (let [{:keys [service_id sender_field]} (get-in env [:payment :fund (:fund params)])
        result (kash/pay (str (gensym (str number "_")))
                         service_id amount
                         (merge
                           {:telefon     (:phone citizen)
                            sender_field (format "%s (%s)" (:first_name citizen) (:phone citizen))}
                           ; this is a quick fix, TODO improve
                           (if (= (:fund params) "Эзгу Aмал")
                             {:client_id "333"}
                             {})))]
    (when-not (= "success" (:status result))
      (throw (ex-info (:reason result) result)))
    result))


(defmethod pay :no-reward
  [{:keys [params amount]}]
  (assoc params :status "ignored"
                :amount amount))


(comment
  (pay {:amount 1000
        :params {:phone "998903753448"}})
  (pay {:amount 500
        :params {:phone "998995118291"}})
  (pay {:amount 1000
        :params {:card "8600312987947625"}})
  (pay {:amount  500
        :params  {:fund "Saxovat Qo'qon"}
        :citizen {:first_name "Olim" :last_name "Saidov" :phone "998974429982"}}))
