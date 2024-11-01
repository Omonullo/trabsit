(ns jarima.handler.misc
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:import [org.apache.commons.io FileUtils])
  (:require
    [jarima.sms :as sms]
    [jarima.util :as util]
    [jarima.kash :as kash]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [clojure.java.io :as io]
    [jarima.redis :as redis]
    [jarima.audit :as audit]
    [cheshire.core :as json]
    [jarima.minio :as minio]
    [clojure.string :as str]
    [medley.core :refer :all]
    [selmer.parser :as parser]
    [buddy.hashers :as hashers]
    [jarima.db.query :as query]
    [jarima.validation :refer :all]
    [clojure.tools.logging :as log]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [jarima.asbt :refer [offense->short-id]]
    [jarima.layout :as layout :refer [t-get t *request* translator mission-translations]]
    [jarima.uzcard :as uzcard]))


(defn translations [{locale :locale}]
  (-> "misc/translations.js"
      (parser/render-file
        {:make-request (not (:prod env))
         :locale       locale
         :translations (when-not (= locale :ru)
                         (-> dictionary
                             (get-in [:translations locale])
                             (json/encode)))})
      (response/ok)
      (response/content-type "text/javascript; charset=utf-8")))


(defn register-form
  [request]
  (if-let [phone (-> request :session :confirmed_phone)]
    (layout/render "misc/register.html" {:phone phone})
    (-> (util/route-path request :misc/login)
        (response/found))))


(def status-names
  {"created"  "queued"
   "failed"   "failed"
   "finished" "succeeded"
   "succeeded" "succeeded"
   "started"  "in-progress"})


(defn queues [request]
  (let [video_encoder        (-> {:select   [:%count.id :video_encoder_status]
                                  :from     [:report]
                                  :where    [:= :review_time nil]
                                  :group-by [:video_encoder_status]}
                                 (db/query))
        extra_video_encoder  (-> {:select   [:%count.id :extra_video_encoder_status]
                                  :from     [:report]
                                  :where    [:and
                                             [:= :review_time nil]
                                             [:!= :extra_video nil]]
                                  :group-by [:extra_video_encoder_status]}
                                 (db/query))
        video_detector       (-> {:select   [:%count.id :video_detector_status]
                                  :from     [:report]
                                  :where    [:and
                                             [:= :review_time nil]]
                                  :group-by [:video_detector_status]}
                                 (db/query))
        extra_video_detector (-> {:select   [:%count.id :extra_video_detector_status]
                                  :from     [:report]
                                  :where    [:and
                                             [:= :review_time nil]
                                             [:!= :extra_video nil]]
                                  :group-by [:extra_video_detector_status]}
                                 (db/query))]
    (response/ok {:video_encoder        (apply merge
                                               (map
                                                 (fn [{:keys [count, video_encoder_status]}]
                                                   {(or (get status-names video_encoder_status) "not-queued") count})
                                                 video_encoder))
                  :extra_video_encoder  (apply merge
                                               (map (fn [{:keys [count, extra_video_encoder_status]}]
                                                      {(or (get status-names extra_video_encoder_status) "not-queued") count})
                                                    extra_video_encoder))
                  :video_detector       (apply merge
                                               (map (fn [{:keys [count, video_detector_status]}]
                                                      {(or (get status-names video_detector_status) "not-queued") count})
                                                    video_detector))
                  :extra_video_detector (apply merge
                                               (map (fn [{:keys [count, extra_video_detector_status]}]
                                                      {(or (get status-names extra_video_detector_status) "not-queued") count})
                                                    extra_video_detector))})))


(defn register
  [request]
  (when-not (-> request :session :confirmed_phone)
    (-> (util/route-path request :misc/login)
        (response/found)
        (response/throw!)))
  (let [params (-> request :parameters :form)]
    (when-let [errors (validate-register params)]
      (layout/error-page!
        {:status 400
         :title  (t "Плохой запрос")
         :data   (util/pretty-errors errors)}))
    (let [citizen
          (-> params
              (assoc :phone (-> request :session :confirmed_phone))
              (assoc :id (random-uuid))
              (assoc :create_time (util/now))
              (q/insert-citizen)
              (db/query-first))]
      (-> (get-in request [:params :next_url]
                  (util/route-path request :citizen.report/index))
          (response/found)
          (assoc :session {:identity (assoc citizen :role "citizen")})))))


(defn root
  [request]
  (-> (response/found
        (case (-> request :identity :role)
          "inspector" (util/route-path request :staff.report/index)
          "admin" (util/route-path request :staff.statistics/index)
          "citizen" (util/route-path request :citizen.report/index)
          (util/route-path request :misc/login)))
      (assoc :flash (:flash request))))


(defn stats []
  {:offense-count (db/query-first {:select [[:%count.* :count]] :from [:offense]} :count)
   :reject-count  (db/query-first {:select [:%count.*] :from   [:offense] :where  [:= :status "rejected"]} :count) :citizen-count (db/query-first {:select [[:%count.* :count]] :from [:citizen]} :count)
   :fine-sum      (/ (or (db/query-first {:select [[:%sum.fine :sum]] :from [:offense]} :sum) 0) 1000000000)
   :reward-sum    (/ (or (db/query-first {:select [[:%sum.reward_amount :sum]] :from [:offense]} :sum) 0) 1000000000)})


(defn show-citizen-login
  [_]
  (-> (layout/render "misc/login.html" (stats))
      (assoc :cookies {"disabled_alerts" {:value ""}})))


(defn show-staff-login
  [_]
  (-> (layout/render "misc/login.html" (merge (stats) {:staff true}))
      (assoc :cookies {"disabled_alerts" {:value ""}})))


(defn send-code
  [request]
  (let [params (-> request :parameters :form)]
    (if-let [errors (validate-send-code params)]
      (response/bad-request {:errors errors})
      (let [[code ttl new?] (-> params :phone redis/set-confirmation-code)]
        (if (and new? (:prod env) (not (sms/send (:phone params) (str (t "Код подтверждения") ": " code))))
          (response/bad-request {:errors {:phone [(t "Не удалось отправить смс.")]}})
          (response/ok {:ttl ttl :code (when-not (:prod env) code)}))))))


;; has tests
(defn citizen-status
  [request]
  (let [params (-> request :parameters :form)
        citizen (-> {:phone [(:phone params)]}
                    (q/select-citizen)
                    (db/query-first))]
    (response/ok
      (if (some? citizen)
        {:status       "registered"
         :has_password (some? (:password citizen))}
        {:status "unregistered"}))))


;; has tests
(defn verify-code [req]
  (let [form (-> req :parameters :form)]
    (if-let [errors (-> form
                        (assoc :stored_code (redis/get-confirmation-code (:phone form)))
                        (validate-phone-code-login))]
      (-> {:errors errors}
          (response/bad-request))
      (do
        (redis/delete-confirmation-code! (:phone form))
        (-> (response/ok)
            (assoc :session (-> (:session req)
                                (assoc :confirmed_phone (:phone form)))))))))


;; has tests
(defn citizen-code-login [req]
  (if-let [phone (get-in req [:session :confirmed_phone])]
    (if-let [citizen (-> {:phone [phone]}
                         (q/select-citizen)
                         (db/query-first))]
      (-> {:redirect (get-in req [:params :next_url] "/")}
          (response/ok)
          (assoc :session (-> (:session req)
                              (dissoc :confirmed_phone)
                              (assoc :identity citizen)
                              (assoc-in [:identity :role] "citizen"))))
      (-> {:redirect (util/route-path req :misc/register
                                      nil (select-keys (:params req) [:next_url]))}
          (response/ok)))
    (-> {:redirect (util/route-path req :misc/login)
         :error    "Ошибка, вы не авторизированны"}
        (response/unauthorized))))


(defn citizen-password-login [req]
  (let [form (-> req :parameters :form)
        citizen (-> {:phone [(:phone form)]}
                    (q/select-citizen)
                    (db/query-first))]
    (if-let [errors (validate-citizen-password-login
                      (assoc form :stored_citizen citizen))]
      (response/bad-request {:errors (map-keys #(if (vector? %) (last %) %) errors)})
      (-> {:redirect (get-in req [:params :next_url] "/")}
          (response/ok)
          (assoc :session (-> (:session req)
                              (assoc :identity citizen)
                              (assoc-in [:identity :role] "citizen")))))))


(defn staff-send-code
  [request]
  (let [params (-> request :parameters :form)]
    (if-let [staff (-> {:username [(:username params)]}
                       (q/select-staff)
                       (db/query-first))]
      (let [[code ttl new?] (-> staff :phone redis/set-confirmation-code)]
        (if (and new? (:prod env) (not (sms/send (:phone staff) (str (t "Код подтверждения") ": " code))))
          (response/bad-request {:errors {:username [(t "Не удалось отправить смс.")]}})
          (response/ok {:ttl ttl :code (when-not (:prod env) code)})))
      (response/bad-request {:errors {:username #{"Пользователь не найден"}}}))))


(defn staff-verify-code [req]
  (let [form (-> req :parameters :form)
        staff (-> {:username [(:username form)]}
                  (q/select-staff)
                  (db/query-first))]
    (if-let [errors (-> form
                        (assoc :phone (:phone staff))
                        (assoc :stored_code (redis/get-confirmation-code (:phone staff)))
                        (validate-phone-code-login))]
      (-> {:errors errors}
          (response/bad-request))
      (do
        (redis/delete-confirmation-code! (:phone staff))
        (-> (response/ok)
            (assoc :session (-> (:session req)
                                (assoc :confirmed_phone (:phone staff)))))))))


(defn staff-login
  [request]
  (util/cond-let
    :let [form (-> request :params)
          errors (validate-username-login form)]

    (some? errors)
    (response/bad-request {:form   form
                           :errors errors
                           :staff  true})

    :let [staff (-> {:from   [:staff]
                     :select [:*]
                     :where  [:and [:= true :active] [:= [:lower :username] [:lower (:username form)]]]}
                    (db/query)
                    (first))]

    (nil? staff)
    ;; not found
    (do (audit/auth-failure
          (:remote-addr request)
          (get-in request [:headers "user-agent"])
          (:username form))
        (response/unprocessable-entity
          {:errors {:username #{"Пользователь не существует."}}}))

    (not (hashers/check (:password form) (:password staff)))
    ;; password does not match
    (do (audit/auth-failure
          (:remote-addr request)
          (get-in request [:headers "user-agent"])
          (:username form))
        (response/unprocessable-entity
          {:form   form
           :errors {:password #{"Пароль неправильный."}}
           :staff  true}))
    (or
      (and (:two_factor_enabled staff)
           (= (get-in request [:session :confirmed_phone]) (:phone staff)))
      (not (:two_factor_enabled staff)))
    ;; either the user has already confirmed phone or two_factor is disabled
    (do
      (audit/auth-success
        (:remote-addr request)
        (get-in request [:headers "user-agent"])
        (:username staff)
        (:role staff)
        (when-let [id (:area_id staff)]
          (db/query-first (q/select-area {:id [id]}) :name_ru))
        (when-let [id (:district_id staff)]
          (db/query-first (q/select-district {:id [id]}) :name_ru)))
      (-> {:redirect (get-in request [:params :next_url] "/")}
          (response/ok)
          (assoc :session (-> (:session request)
                              (assoc :identity staff)))
          (dissoc-in [:session :confirmed_phone])))

    (and (:two_factor_enabled staff)
         (or (not (get-in request [:session :confirmed_phone]))
             (not (= (get-in request [:session :confirmed_phone]) (:phone staff)))))
    ;; two factor is enabled and phone is not confirmed
    (response/unprocessable-entity
      {:two_factor_enabled true
       :errors             {:code #{"Введите код подтверждения"}}})
    :else
    (throw "Security issue")))



(defn logout
  [request]
  (-> (util/route-path request (if (= "citizen" (-> request :identity :role))
                                 :misc/login :misc/staff))
      (response/found)
      (assoc :session (dissoc (:session request) :identity))))


(defn download-converter
  [_]
  (when (:converter-file env)
    (-> (response/file-response (:converter-file env))
        (response/header
          "Content-Disposition"
          (format "attachment; filename=\"%s\""
            (-> (:converter-file env) (io/file) (.getName)))))))


(defn introduce-converter
  [_]
  (layout/render
    "misc/converter.html"
    (when (:converter-file env)
      (let [file (io/file (:converter-file env))]
        {:name (.getName file)
         :size (FileUtils/byteCountToDisplaySize (.length file))}))))


(defn faq [_]
  (layout/render
    "misc/faq.html"
    {:faqs       (-> {:select [:*]
                      :from   [:faq]}
                     (db/query))
     :categories (->> (query/select-faq nil)
                      (db/query)
                      (map #(t-get % :category))
                      (distinct))}))


(defn raise [_]
  (throw (ex-info "This exception is intentional" {:hello "world"})))


(defn show-change-password
  ([request]
   (show-change-password nil nil request))
  ([params errors req]
   (layout/render
     "misc/password.html"
     {:errors errors
      :params params
      :staff  (some? (get #{"admin" "inspector"} (-> req (:identity) (:role))))})))


;; has tests for citizen, TODO for staff
(defn change-password
  [req]
  (let [user (-> req :identity)
        form (-> req :parameters :form)]
    (if (some? (get #{"admin" "inspector"} (-> req (:identity) (:role))))
      (if-let [errors (validate-change-staff-password
                        (assoc form :stored_staff user))]
        (response/bad-request {:errors (map-keys #(if (vector? %) (last %) %) errors)})
        (do
          (-> (q/update-staff
                (:id user)
                {:password (hashers/derive (:new_password form))})
              (db/query-first))
          (->
            {:redirect (util/route-path req :misc/root)}
            (response/ok)
            (assoc :flash {:alert {:success (t "Пароль обновлен.")}}))))
      (let [phone (-> req :session :confirmed_phone)]
        (if-let [errors (validate-change-citizen-password
                          (assoc form :phone phone
                                      :stored_citizen (-> {:phone [phone]}
                                                          (q/select-citizen)
                                                          (db/query-first))))]
          (if (:stored_citizen errors)
            (response/unauthorized {:redirect (util/route-path req :misc/register)})
            (response/bad-request {:errors errors}))
          (do
            (-> (q/update-citizen
                  {:phone [phone]}
                  {:password (hashers/derive (:new_password form))})
                (db/query-first))
            (-> (citizen-code-login req)
                (assoc :flash {:alert {:success (t "Пароль обновлен.")}}))))))))





(defn show-search
  [request]
  (layout/render
    "misc/search.html"
    {:offenses (-> {:select [:*]
                    :from   [:offense]
                    :where  [:and
                             [:!= :forward_time nil]
                             [:or
                              [:= :vehicle_id (-> request :parameters :query :q)]
                              [:= :fine_id (-> request :parameters :query :q)]]]}
                   (db/query (fn [offense]
                               (-> offense
                                   (assoc :short-id (offense->short-id offense))
                                   (assoc :report
                                          (-> {:id [(:report_id offense)]}
                                              (q/select-report)
                                              (db/query)
                                              (first)
                                              (update :thumbnail minio/get-public-url)))))))}))


(defn missing-translation
  [request]
  (when-let [text (:text (:params request))]
    (log/warn "Translation is missing" (:locale request) text)
    (swap! mission-translations update (:locale request) #(conj % text)))
  (response/no-content))


(defn terms
  [_]
  (layout/render
    "misc/terms.html"))


(defn verify-card
  [request]
  (let [params (-> request :parameters :form)]
    (util/cond-let
      :let [errors (validate-card params)]

      (some? errors)
      (-> {:error (single-error errors)}
          (response/ok))

      :else
      (try
        (let [info (uzcard/card-info (:card params))]
          (if (= 0 (:state info))
            (-> {:owner (str/join " " (map str/capitalize (str/split (:owner info) #"\s")))
                 :bank  (:bank info)}
                (response/ok))
            (throw (ex-info "Card is blocked" {:state (:state info)}))))
        (catch Exception _
          (-> {:owner "Сервис проверки карты не доступен"
               :bank  ""}
              (response/ok)))))))



(def routes
  [["/"
    {:name :misc/root
     :get  #'root}]

   ["/raise"
    {:name :misc/raise
     :get  #'raise}]

   ["/queues"
    {:name :misc/queues
     :get  #'queues}]

   ["/faq"
    {:name :misc/faq
     :get  #'faq}]

   ["/logout"
    {:name :misc/logout
     :post #'logout}]

   ["/send-code"
    {:name       :misc/send-code
     :post       #'send-code
     :parameters {:form :misc.send-code/form}}]

   ["/staff-send-code"
    {:name       :misc.staff/send-code
     :post       #'staff-send-code
     :parameters {:form :misc.staff-send-code/form}}]


   ["/staff-verify-code"
    {:name       :misc.staff/verify-code
     :post       #'staff-verify-code
     :parameters {:form :misc.staff-verify-code/form}}]

   ["/status"
    {:name       :misc/citizen-status
     :post       #'citizen-status
     :parameters {:form :misc.send-code/form}}]
   ["/verify-code"
    {:name       :misc/verify-code
     :post       #'verify-code
     :parameters {:form :misc.verify-code/form}}]

   ["/login"
    {:name       :misc/login
     :get        #'show-citizen-login
     :post       #'citizen-password-login
     :parameters {:form :misc.login/form}}]

   ["/code-login"
    {:name :misc/code-login
     :post #'citizen-code-login}]

   ["/staff"
    {:name :misc/staff
     :get  #'show-staff-login
     :post #'staff-login}]

   ["/register"
    {:name       :misc/register
     :get        #'register-form
     :post       #'register
     :parameters {:form :misc.register/form}}]

   ["/converter/download"
    {:name :misc/download-converter
     :get  #'download-converter}]

   ["/converter"
    {:name :misc/converter
     :get  #'introduce-converter}]

   ["/password"
    {:name       :misc/change-password
     :get        #'show-change-password
     :post       #'change-password
     :parameters {:form :misc.change-password/form}}]

   ["/search"
    {:name       :misc/search
     :get        #'show-search
     :parameters {:query :misc.search/query}}]

   ["/verify-card"
    {:name       :misc/verify-card
     :post       #'verify-card
     :parameters {:form :misc.verify-card/form}}]

   ["/terms"
    {:name :misc/terms
     :get  #'terms}]

   (when-not (:prod env)
     ["/missing-translation"
      {:name :misc/missing-translation
       :get  missing-translation}])

   ["/js/translations.js"
    {:name :misc/translations
     :get  #'translations}]])
