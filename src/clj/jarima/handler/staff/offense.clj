(ns jarima.handler.staff.offense
  (:require
    [jarima.util :as util]
    [jarima.spec :as spec]
    [cuerdas.core :as str]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [jarima.minio :as minio]
    [medley.core :refer [index-by]]
    [clojure.tools.logging :as log]
    [jarima.reward :refer [reward-type]]
    [jarima.layout :as layout :refer [t]]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [dk.ative.docjure.spreadsheet :as excel]
    [ring.util.io :refer [piped-input-stream]]
    [jarima.handler.staff.report :as staff.report]
    [medley.core :refer [uuid random-uuid map-vals filter-vals]]))


(defn before-index
  [request]
  (update-in
    request [:parameters :query :sort]
    (fn [v]
      (or v (if (= "admin" (-> request :identity :role))
              "create_time_desc"
              "create_time_asc")))))


(defn index
  [{{query :query} :parameters identity :identity :as request}]
  (layout/render
    "staff/offense/list.html"
    (-> (util/paged-query
          (partial q/select-offense
                   (-> (if (:only_my query)
                         (assoc query :creator_staff_id (:id identity))
                         query)
                       (staff.report/restrict-filter identity)
                       (util/vectorify-vals (complement #{:founder_role :vehicle_id :create_time :sort :incident_time}))))
          (:page query))
        (assoc :request request)
        (assoc :statuses (keys spec/offense-statuses))
        (assoc :offense-types (db/query (q/select-offense-type {})))
        (assoc :articles (db/query (q/select-article {})))
        (assoc :responses (db/query (q/select-response {})))
        (assoc :inspector (db/query (q/select-staff {:role ["inspector"]})))
        (assoc :failure_messages (db/query {:select [:failure_message]
                                            :modifiers [:distinct]
                                            :from [:offense]
                                            :where [:and
                                                    [:!= nil :failure_message]
                                                    [:= "failed" :status]]} :failure_message))
        (update :paged-rows
                (partial map
                         (fn [offense]
                           (assoc offense :report
                             (let [report
                                   (-> {:id [(:report_id offense)]}
                                       (q/select-report)
                                       (db/query)
                                       (first))]
                               (-> report
                                   (update :thumbnail minio/get-public-url)
                                   (assoc :citizen (-> {:id [(:citizen_id report)]}
                                                       (q/select-citizen)
                                                       (db/query)
                                                       (first))))))))))))

(defn create-excel [offenses]
  (fn [stream]
    (try
      (let [book   (excel/create-workbook "report" [])
            sheet  (excel/select-sheet "report" book)]

        (excel/add-row!
          sheet
          [(str (t "Гражданин") ": " "№")
           (str (t "Гражданин") ": " (t "Имя"))
           (str (t "Гражданин") ": " (t "Фамилия"))
           (str (t "Гражданин") ": " (t "Отчество"))
           (str (t "Гражданин") ": " (t "Номер телефона"))
           (str (t "Гражданин") ": " (t "Номер карты"))
           (str (t "Гражданин") ": " (t "Область"))
           (str (t "Гражданин") ": " (t "Район"))
           (str (t "Гражданин") ": " (t "Адрес"))
           (str (t "Гражданин") ": " (t "Почтовый индекс"))
           (str (t "Гражданин") ": " (t "Дополнительный номер"))
           (str (t "Гражданин") ": " (t "Дата регистрации"))

           (str (t "Видеозапись") ": " "№")
           (str (t "Видеозапись") ": " (t "Область"))
           (str (t "Видеозапись") ": " (t "Район"))
           (str (t "Видеозапись") ": " (t "Адрес"))
           (str (t "Видеозапись") ": " (t "Координаты (lat, lng)"))
           (str (t "Видеозапись") ": " (t "Статус"))
           (str (t "Видеозапись") ": " (t "Время нарушения"))
           (str (t "Видеозапись") ": " (t "Время создания заявки"))
           (str (t "Видеозапись") ": " (t "Время рассмотрения"))
           (str (t "Видеозапись") ": " (t "Вознаграждение"))

           (str (t "Правонарушение") ": " "№")
           (str (t "Правонарушение") ": " (t "Номер транспорта"))
           (str (t "Правонарушение") ": " (t "Описание"))
           (str (t "Правонарушение") ": " (t "Статус"))
           (str (t "Правонарушение") ": " (t "Статья"))
           (str (t "Правонарушение") ": " (t "Причина отклонения"))
           (str (t "Правонарушение") ": " (t "Пояснение причины отклонения"))
           (str (t "Правонарушение") ": " (t "Дата отправки КСУБД"))
           (str (t "Правонарушение") ": " (t "Время ошибки отправки КСУБД"))
           (str (t "Правонарушение") ": " (t "Ошибка отправки КСУБД"))
           (str (t "Правонарушение") ": " (t "Номер постановления"))
           (str (t "Правонарушение") ": " (t "Дата постановления"))
           (str (t "Правонарушение") ": " (t "Дата отклонения судом"))
           (str (t "Правонарушение") ": " (t "Дата оплаты штрафа"))
           (str (t "Правонарушение") ": " (t "Сумма штрафа"))
           (str (t "Правонарушение") ": " (t "Сумма вознаграждения"))

           (str (t "Инспектор") ": " (t "Имя"))
           (str (t "Инспектор") ": " (t "Фамилия"))
           (str (t "Инспектор") ": " (t "Отчество"))
           (str (t "Инспектор") ": " (t "Звание"))
           (str (t "Инспектор") ": " (t "Номер телефона"))

           (str (t "Вознаграждение") ": " (t "Тип"))
           (str (t "Вознаграждение") ": " (t "Получатель"))
           (str (t "Вознаграждение") ": " (t "Сумма"))
           (str (t "Вознаграждение") ": " (t "Статус"))
           (str (t "Вознаграждение") ": " (t "Дата создания"))
           (str (t "Вознаграждение") ": " (t "Время оплаты"))
           (str (t "Вознаграждение") ": " (t "Время ошибки"))
           (str (t "Вознаграждение") ": " (t "Причина ошибки"))])

        (excel/add-rows!
          sheet
          (map (fn [offense]
                 [(-> offense :report :citizen :number)
                  (-> offense :report :citizen :first_name)
                  (-> offense :report :citizen :last_name)
                  (-> offense :report :citizen :middle_name)
                  (-> offense :report :citizen :phone util/format-phone)
                  (-> offense :report :citizen :card util/format-card)
                  (-> offense :report :citizen :area (layout/t-get :name))
                  (-> offense :report :citizen :district (layout/t-get :name))
                  (-> offense :report :citizen :address)
                  (-> offense :report :citizen :zipcode)
                  (-> offense :report :citizen :second_phone)
                  (-> offense :report :citizen :create_time util/unparse-local-date-time-precise)

                  (-> offense :report :number)
                  (-> offense :report :area (layout/t-get :name))
                  (-> offense :report :district (layout/t-get :name))
                  (-> offense :report :address)
                  (str (-> offense :report :lat) ", " (-> offense :report :lng))
                  (-> offense :report :status spec/report-statuses :name t)
                  (-> offense :report :incident_time util/unparse-local-date-time-precise)
                  (-> offense :report :create_time util/unparse-local-date-time-precise)
                  (-> offense :report :review_time util/unparse-local-date-time-precise)
                  (-> offense :report :reward_params layout/reward)

                  (-> offense :number)
                  (-> offense :vehicle_id)
                  (-> offense :testimony)
                  (-> offense :status spec/offense-statuses :name t)
                  (-> offense :article :number (str " " (-> offense :article (layout/t-get :text))))
                  (-> offense :response (layout/t-get :name))
                  (-> offense :extra_response)
                  (-> offense :forward_time util/unparse-local-date-time-precise)
                  (-> offense :failure_time util/unparse-local-date-time-precise)
                  (-> offense :failure_message)
                  (-> offense :fine_id)
                  (-> offense :fine_date util/unparse-local-date-time-precise)
                  (-> offense :dismiss_time util/unparse-local-date-time-precise)
                  (-> offense :pay_time util/unparse-local-date-time-precise)
                  (-> offense :fine)
                  (-> offense :reward_amount)

                  (-> offense :report :inspector :first_name)
                  (-> offense :report :inspector :last_name)
                  (-> offense :report :inspector :middle_name)
                  (-> offense :report :inspector :rank)
                  (-> offense :report :inspector :phone util/format-phone)

                  (some-> offense :reward :type spec/reward-types :name t)
                  (some-> offense :reward :params vals first)
                  (some-> offense :reward :amount)
                  (some-> offense :reward :status spec/reward-statuses :name t)
                  (some-> offense :reward :create_time util/unparse-local-date-time-precise)
                  (some-> offense :reward :pay_time util/unparse-local-date-time-precise)
                  (some-> offense :reward :failure_time util/unparse-local-date-time-precise)
                  (some-> offense :reward :failure_message)])
               offenses))

        (doseq [cell (-> (excel/row-seq sheet)
                         (first)
                         (excel/cell-seq))]
          (.setColumnWidth
            (.getSheet cell)
            (.getColumnIndex cell)
            6000))

        (excel/save-workbook-into-stream! stream book))
      (catch Throwable t
        (log/error t)
        (throw t)))))

(defn query-relation
  [query-fn rel-key coll]
  (when-let [ids (seq (distinct (keep rel-key coll)))]
    (db/query (query-fn {:id ids}))))


(defn assoc-relation
  [coll rel-key rel-coll]
  (let [index     (index-by :id rel-coll)
        assoc-key (-> (name rel-key) (str/replace #"_id$" "") (keyword))]
    (map #(assoc % assoc-key (index (rel-key %))) coll)))


(defn export
  [{{query :query} :parameters identity :identity :as request}]
  (if (not= "admin" (:role identity))
    (->> {:status  403
          :refresh [1 "/"]
          :title   (str "Доступ запрещен")}
         (layout/error-page))
    (let [offenses   (-> (staff.report/restrict-filter query identity)
                         (util/vectorify-vals (complement #{:vehicle_id :create_time :sort}))
                         (q/select-offense)
                         (db/query))

          articles   (query-relation q/select-article :article_id offenses)
          responses  (query-relation q/select-response :response_id offenses)
          reports    (query-relation q/select-report :report_id offenses)
          rewards    (query-relation q/select-reward :reward_id offenses)
          citizens   (query-relation q/select-citizen :citizen_id reports)
          inspectors (query-relation q/select-staff :inspector_id reports)
          areas      (query-relation q/select-area :area_id (concat reports citizens inspectors))
          districts  (query-relation q/select-district :district_id (concat reports citizens inspectors))

          inspectors (-> inspectors
                         (assoc-relation :area_id areas)
                         (assoc-relation :district_id districts))

          citizens   (-> citizens
                         (assoc-relation :area_id areas)
                         (assoc-relation :district_id districts))

          reports    (-> reports
                         (assoc-relation :area_id areas)
                         (assoc-relation :district_id districts)
                         (assoc-relation :inspector_id inspectors)
                         (assoc-relation :citizen_id citizens))

          offenses   (-> offenses
                         (assoc-relation :article_id articles)
                         (assoc-relation :response_id responses)
                         (assoc-relation :reward_id rewards)
                         (assoc-relation :report_id reports))]

      (-> (create-excel offenses)
          (piped-input-stream)
          (response/ok)
          (response/header "content-disposition" "attachment; filename=\"offenses.xlsx\"")
          (response/content-type "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))))




(defn log [request]
  (if-let [offense (-> {:id (-> request :parameters :path :id)}
                       (staff.report/restrict-filter (:identity request))
                       (util/vectorify-vals)
                       (q/select-offense)
                       (db/query)
                       (first))]
    (response/ok {:log (:asbt_log offense)})
    (layout/error-page!
      {:status 404
       :title  "Нарушение не найдено"})))


(defn reforward [request]
  (let [offense (-> {:id (-> request :parameters :path :id)
                     :status "failed"}
                    (staff.report/restrict-filter (:identity request))
                    (util/vectorify-vals)
                    (q/select-offense)
                    (db/query)
                    (first))]
    (when-not offense
      (layout/error-page!
        {:status 404
         :title  "Нарушение не найдено"}))
    (-> (q/update-offense (:id offense) {:failure_message nil})
        (db/query))
    (-> (util/route-path request :staff.report/view {:id (:report_id offense)})
        (str "#" (:number offense))
        (response/found))))


(defn reforward-all [request]
  (doseq [offense (-> request
                      :parameters
                      :form
                      (assoc :status "failed")
                      (staff.report/restrict-filter (:identity request))
                      (util/vectorify-vals (complement #{:founder_role :vehicle_id :create_time :sort :incident_time}))
                      (q/select-offense)
                      (db/query))]
    (-> (q/update-offense (:id offense) {:failure_message nil})
        (db/query)))
  (-> (util/route-path request :staff.report/index)
      (response/found)
      (util/recall-referer request (:base-url env))))



(def routes
  ["/offenses"
   [""
    {:name       :staff.offense/index
     :get        (comp #'index #'before-index)
     :post       (comp #'export #'before-index)
     :parameters {:query :staff.offense.index/query}}]
   ["/reforward"
    {:name       :staff.offense/reforward-all
     :post       #'reforward-all
     :parameters {:form :staff.offense.index/query}}]
   ["/:id/log"
    {:name       :staff.offense/log
     :get        #'log
     :parameters {:path {:id :jarima.spec/uuid}}}]
   ["/:id/reforward"
    {:name       :staff.offense/reforward
     :post        #'reforward
     :parameters {:path {:id :jarima.spec/uuid}}}]])
