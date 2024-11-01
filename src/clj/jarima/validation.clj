(ns jarima.validation
  (:require [clojure.set :as set]
            [jarima.util :as util]
            [jarima.spec :as spec]
            [jarima.uzcard :as uzcard]
            [jarima.spec :as spec]
            [jarima.db.core :as db]
            [jarima.db.query :as q]
            [jarima.redis :as redis]
            [clojure.string :as str]
            [buddy.hashers :as hashers]
            [jarima.config :refer [env]]
            [jarima.db.query :as queries]
            [jarima.ffmpeg :refer [probe]]
            [jarima.config :refer [dictionary]]
            [medley.core :refer [map-keys]]
            [validateur.validation :refer :all :exclude [inclusion-of]]
            [clojure.tools.logging :as log]
            [jarima.kash :as kash]))


(defn inclusion-of
  "Like validateur.validation/inclusion-of, but allows to specify :key in map to check inclusion of"
  [attribute & {:keys [allow-nil in message blank-message message-fn]
                :or   {allow-nil     false, message "must be one of: ",
                       blank-message "can't be blank"}}]
  (let [f            (if (vector? attribute) get-in get)
        g            (if (vector? in) get-in get)
        blank-msg-fn (fn [m] (if message-fn
                               (message-fn :blank m attribute)
                               blank-message))]
    (fn [m]
      (let [v      (f m attribute)
            in     (if (set? in) in (g m in))
            msg-fn (fn [m] (if message-fn (message-fn :inclusion m attribute in)
                                          (str message (clojure.string/join ", " in))))]
        (if (nil? v)
          (if allow-nil
            [true {}]
            [false {attribute #{(blank-msg-fn m)}}])
          (if (contains? (set in) v)
            [true {}]
            [false {attribute #{(msg-fn m)}}]))))))


(defn subset-of
  [attribute & {:keys [of message message-fn]}]
  (let [f (if (vector? attribute) get-in get)]
    (fn [m]
      (let [subset     (set (f m attribute))
            in         (set (cond
                              (keyword? of) (get m of)
                              (vector? of) (get-in m of)
                              (set? of) of))
            message-fn (or message-fn (fn [_ _] (str (or message "should be superset"))))]
        (if (set/subset? subset in)
          [true {}] [false {attribute #{(message-fn subset in)}}])))))


(defn superset-of
  [attribute & {:keys [of message message-fn]}]
  (let [f (if (vector? attribute) get-in get)]
    (fn [m]
      (let [subset     (set (f m attribute))
            in         (set (cond
                              (keyword? of) (get m of)
                              (vector? of) (get-in m of)
                              (set? of) of))
            message-fn (or message-fn (fn [_ _] (str (or message "should be superset"))))]
        (if (set/superset? subset in)
          [true {}]
          [false {attribute #{(message-fn subset in)}}])))))



(defn compose-some-sets
  "Works like validate-some, but with validation sets"
  [& fns]
  (fn [m]
    (loop [[fn & rest] fns]
      (if-not fn
        nil
        (let [errors (fn m)]
          (if (seq errors)
            errors
            (recur rest)))))))


(defn nest-set [attr vset]
  (fn [m]
    (when-let [errors (not-empty (vset m))]
      (nest attr errors))))


(defn nest-some-validators
  "Works as validate-some, but each key becomes"
  [key & validators]
  (fn [m]
    (loop [[v & rest] validators]
      (if-not v
        [true #{}]
        (let [[passed? errors :as result] (v m)]
          (if passed?
            (recur rest)
            [passed?
             (map-keys #(vec (flatten [key %])) errors)]))))))


(defn validate-all
  [& validators]
  (fn [m]
    (reduce
      (fn [result v]
        (let [r (v m)]
          (-> result
              (update 0 #(and % (first r)))
              (update 1 #(merge-with set/union % (second r))))))
      [true {}]
      validators)))


(defn is-phone-of [field]
  (format-of field :format #"998\d{9}" :allow-nil true :message "Номер телефона неверен"))


(defn is-card-of [field]
  (format-of field :format #"8600\d{12}" :allow-nil false :message "Карта введена неверно" :blank-message "Укажите номер карты"))


(defn is-supported-card-of [field]
  (format-of field :format #"8600(?!34)\d{12}" :allow-nil true :message "Карты банка КДБ не поддерживаются"))


(defn is-real-card-of [field]
  (let [f (if (vector? field) get-in get)]
    (fn [m]
      (try
        (let [card (f m field)
              card-type (uzcard/card-type card)
              card-info (when (-> card-type :is_uzcard boolean)
                          (uzcard/card-info card))]
          (if-let [message (:message card-info)]
            [false {field #{message}}]
            [true {}]))
        (catch Exception e
          (log/error e)
          [false {field #{"Сервис проверки карты не доступен"}}])))))


(defn is-bank-of [field]
  (format-of field :format #"\d{20}" :allow-nil true :message "Рассчетный счет неверный"))


(defn absence-of
  [field]
  (fn [data]
    (if (some? (get data field))
      [false {field #{"must be blank"}}]
      [true {}])))


(defn equality-of [ks & {:keys [message field]}]
  (fn [data]
    (if (->> ks
             (map #(if (vector? %) (get-in data %)
                                   (get data %)))
             (apply =))
      [true {}]
      [false {(or field (first ks))
              #{(or message "must be equal")}}])))


(defn existence-of
  [field table & {:keys [target-column exclude-column update-fn message]
                  :or   {target-column  :id
                         exclude-column nil
                         message        "must exist in db"
                         update-fn      identity}}]
  (fn [data]
    (let [data (update data field update-fn)]
      (if (or
            (nil? data)
            (nil? (field data))
            (try
              (-> (queries/count-value
                    table
                    [target-column (field data)]
                    (when (some? exclude-column)
                      [exclude-column (exclude-column data)]))
                  (db/query-first :count)
                  (zero?))
              (catch Exception _
                true)))
        [false {field #{message}}]
        [true {}]))))


(defn temp-video-of [field]
  (fn [data]
    (if (and (field data) (-> (field data) (redis/get-temp-video) (first)))
      [true {}]
      [false {field #{"Указанного видео не существует. Повторите отправку"}}])))


(defn uniqueness-of
  [field table & {:keys [target-column exclude-column message]
                  :or   {target-column  field
                         message        "must be unique"
                         exclude-column :id}}]
  (fn [data]
    (if (or (nil? (field data))
            (-> (queries/count-value table
                  [target-column (field data)]
                  (when (some? exclude-column)
                    [exclude-column (exclude-column data)]))
                (db/query-first :count)
                (db/with-debug)
                (zero?)))
      [true {}]
      [false {field #{message}}])))


(defn nillify
  [validator]
  (fn [data]
    (let [errors (validator data)]
      (when (invalid? errors)
        errors))))


(defn validate-if
  [predicate true-validator false-validator]
  (fn [m]
    (if (predicate m)
      (true-validator m)
      (false-validator m))))


(defn single-error
  ([error-map]
   (-> error-map first val first))
  ([error-map attributes]
   (->> (if (set? attributes) attributes #{attributes})
        (keep #(errors % error-map))
        (ffirst))))


(defn validate-vals-of [field & validators]
  (let [v (apply validate-all validators)]
    (fn [data]
      (let [err-map (apply merge-with set/union
                           (map (fn [[index item]]
                                  (let [[_ errs] (v item)]
                                    (nest [field index] errs))) (field data)))]
        [(empty? err-map) err-map]))))


(defn password-match-of
  [field1 field2 & {:keys [message]}]
  (fn [data]
    (if (hashers/check ((if (vector? field1) get-in get) data field1)
                       ((if (vector? field2) get-in get) data field2))
      [true {}]
      [false {field1 [message]}])))


;; has tests
(def validate-change-staff-password
  (nillify
    (validation-set
      (validate-some
        (presence-of :old_password :message "Добавьте пароль")
        (presence-of [:stored_staff :id] :message "Ошибка, вы не авторизированны")
        (password-match-of :old_password [:stored_staff :password] :message "Неверный пароль"))
      (validate-some
        (presence-of :new_password :message "Введите новый пароль")
        (validate-by :new_password #(< 8 (count %)) :message "Пароль слишком короткий"))
      (validate-some
        (presence-of :repeated_new_password :message "Введите новый пароль")
        (equality-of [:new_password :repeated_new_password] :message "Пароли не совпадают")))))


;; has tests
(def validate-change-citizen-password
  (nillify
    (validation-set
      (validate-some
        (presence-of :phone :message "Вы не авторизированы")
        (presence-of :stored_citizen :message "Вы не авторизированы")
        (presence-of :new_password :message "Введите пароль")
        (validate-by :new_password #(< 6 (count %)) :message "Пароль слишком короткий")
        (validate-with-predicate :new_password
          #(not (or (str/includes? (:new_password %) (:phone %))
                    (str/includes? (:new_password %) (subs (:phone %) 3))
                    (str/includes? (:new_password %) (subs (:phone %) 5))))
          :message "Пароль не может включать в себя номер телефона"))
      (validate-some
        (presence-of :repeated_new_password :message "Введите пароль")
        (equality-of [:new_password :repeated_new_password] :message "Пароли не совпадают")))))


;; has tests
;; for citizens only
(def validate-phone-code-login
  (nillify
    (validation-set
      (validate-some
        (presence-of :phone :message "Укажите телефонный номер")
        (is-phone-of :phone))
      (validate-some
        (presence-of :code :message "Укажите код подтверждения.")
        (format-of :code :format #"\d{6}" :message "Укажите правильный код подтверждения.")
        (equality-of [:code :stored_code] :message "Укажите правильный код подтверждения.")))))

(def validate-sms
  (nillify
    (validation-set
      (validate-some
        (presence-of :phone :message "Укажите телефонный номер")
        (is-phone-of :phone))
      (validate-some
        (presence-of :message :message "Укажите сообщение.")))))


;; has tests
(def validate-citizen-password-login
  (nillify
    (validation-set
      (validate-some
        (presence-of :phone :message "Введите номер")
        (presence-of :password :message "Введите пароль")
        (equality-of [:phone [:stored_citizen :phone]] :message "Вы не зарегестрированы")
        (presence-of [:stored_citizen :password] :message "Не влючён пароль")
        (password-match-of :password [:stored_citizen :password]
                           :message "Неправильный пароль")))))


(def validate-username-login
  (nillify
    (validation-set
      (presence-of :username)
      (presence-of :password))))


(def validate-send-code
  (nillify
    (validation-set
      (presence-of :phone)
      (is-phone-of :phone))))


(defn is-valid-video-of
  [attr]
  (fn [data]
    (try
      (let [{:keys [duration]}
            (probe (get-in data [attr :tempfile]))]
        (cond
          (not (<= duration 120)) [false {attr #{"Видео не может быть длиннее 120 секунд"}}]
          :else [true {}]))
      (catch Throwable e
        (log/info e "Video invalid format")
        [false {attr #{"Недействительный формат видео"}}]))))


(def validate-asbt-notification
  (nillify
    (validation-set
      (validate-some
        (presence-of :pId)
        (validate-by :pId string? :message "Должен иметь тип string")
        (length-of :pId :is 32)
        (existence-of :pId :offense :update-fn util/guid->uuid))
      (validate-when (comp not-empty :pSeryNumber)
        (validate-some
          (presence-of :pSeryNumber)
          (validate-by :pSeryNumber string? :message "Должен иметь тип string")
          (length-of :pSeryNumber :within (range 0 14))))
      (validate-some
        (presence-of :pDate)
        (validate-by :pDate string? :message "should be string")
        (format-of :pDate :format #"((0[1-9]|[12]\d|3[01]).(0[1-9]|1[0-2]).[12]\d{3})"
                   :message "Дата должна быть формата DD.MM.YYYY"))
      (validate-some
        (presence-of :pStatus)
        (validate-by :pStatus int? :message "Должен иметь тип int")
        (inclusion-of :pStatus :in #{201 208 209})))))


(def validate-article
  (nillify
    (validation-set
      (validate-some
        (presence-of :id :message "Поле не может быть пустым")
        (validate-when #(not (and (:old-id %) (= (:old-id %) (:id %))))
          (uniqueness-of
            :id :article
            :exclude-column nil
            :message "Такой КСБД код уже существует")))
      (validate-some
        (presence-of :factor)
        (numericality-of :factor :gt 0)))))


(def validate-offense-type
  (nillify
    (validation-set
      (validate-some
        (presence-of :name_ru :message "Введите название")))))


(def validate-video-upload
  (nillify
    (validation-set
      (validate-some
        (presence-of :video :message "Отправьте видео")
        (is-valid-video-of :video)))))


(def validate-profile-edit
  (nillify
    (validation-set
      (presence-of #{:first_name :middle_name :last_name :address :zipcode})
      (is-phone-of :second_phone)
      (validate-some
        (presence-of :area_id)
        (existence-of :area_id :area))
      (validate-when #(contains? % :card)
        (validate-some
          (is-card-of :card)
          (is-supported-card-of :card)
          (is-real-card-of :card)))
      (validate-some
        (presence-of :district_id)
        (existence-of :district_id :district)))))


(def validate-citizen-edit
  (nillify
    (validation-set
      (presence-of #{:first_name :middle_name :last_name :address})
      (is-phone-of :phone)
      (is-phone-of :second_phone)
      (existence-of :id :citizen)
      (uniqueness-of
        :phone :citizen
        :exclude-column :id
        :message "Телефон уже используется")
      (validate-when #(and (not-empty (:new_password %)) (:has_password %))
        (validate-by :new_password #(< 6 (count %)) :message "Пароль слишком короткий"))

      (validate-some
        (presence-of :area_id)
        (existence-of :area_id :area))
      (validate-when #(contains? % :card)
        (validate-some
          (is-card-of :card)
          (is-supported-card-of :card)
          (is-real-card-of :card)))
      (validate-some
        (presence-of :district_id)
        (existence-of :district_id :district)))))


(def validate-card
  (nillify
    (validation-set
      (validate-some
        (is-card-of :card)
        (is-supported-card-of :card)
        (is-real-card-of :card)))))


(def validate-register
  (nillify
    (validation-set
      (presence-of #{:first_name :middle_name :last_name :address})
      (is-phone-of :second_phone)
      (length-of :first_name :within (range 3 33))
      (length-of :middle_name :within (range 3 33))
      (length-of :last_name :within (range 3 33))
      (validate-when #(contains? % :card)
        (validate-some
          (is-card-of :card)
          (is-supported-card-of :card)
          (is-real-card-of :card)))
      (validate-some
        (presence-of :area_id)
        (existence-of :area_id :area))
      (validate-some
        (presence-of :district_id)
        (existence-of :district_id :district)))))


(def validate-mwage
  (nillify
    (validation-set
      (presence-of :value)
      (numericality-of :value :only-integer true :gt 0))))


(def validate-review-report
  (nillify
    (validation-set
      (presence-of :address :message "Укажите адрес")
      (validate-some
        (presence-of :district_id :message "Указан неправильный район")
        (existence-of :district_id :district :message "Указанный район недействителен"))
      (presence-of :offenses :message "Укажите нарушения")
      (validate-some
        (validate-by :offenses not-empty :message "Укажите нарушения")
        (validate-vals-of :offenses
          (validate-some
            (presence-of :status :message "Укажите статус")
            (inclusion-of :status :in #{"accepted" "rejected"} :message "Укажите статус"))

          (validate-when :id
            (existence-of :id :offense :message "Указанное нарушение не действительно"))

          (validate-when (complement :id)
            (presence-of :vehicle_id :message "Укажите номер машины"))

          (validate-when (comp #{"accepted"} :status)
            (validate-all
              (validate-some
                (presence-of :article_id :message "Укажите статью")
                (existence-of :article_id :article :target-column :id :message "Указанная статья не существует"))
              (presence-of :vehicle_img :message "Укажите фото машины")
              (presence-of :vehicle_id_img :message "Укажите фото номера машины")))

          (validate-when (comp #{"rejected"} :status)
            (validate-some
              (presence-of :response_id :message "Не возможно отклонить без причины")
              (existence-of :response_id :response :message "Не возможно отклонить без причины"))))))))

(comment
  (let [review-report-result {:address     "Davr"
                              :district_id #uuid"6b535378-fe2f-4faf-a86e-fa81c02f8bff"
                              :offenses    {123 {:status "rejected"}}}]
    (validate-review-report review-report-result)))


(def validate-create-report
  (nillify
    (validation-set
      (validate-some
        (presence-of :reward_params :message "Выберите вознаграждение")
        (validate-by :reward_params #(= (count (keys %)) 1) :message "Вознаграждение неверно")
        (validity-of :reward_params
                     (all-keys-in (->> spec/reward-types
                                       (remove :unavailable)
                                       (keys)
                                       (map keyword)
                                       (set))
                                  :unknown-message "Вознаграждение неверно"))
        (validate-when (comp #(contains? % :phone) :reward_params)
          (is-phone-of [:reward_params :phone]))
        (validate-when (comp #(contains? % :no-reward) :reward_params)
          (validate-by [:reward_params :no-reward] identity))
        (validate-when (fn [report] (contains? (:reward_params report) :card))
          (validate-some
            (validate-by [:reward_params :card] boolean?)))
        (validate-when (comp :bank :reward_params)
          (is-bank-of [:reward_params :bank]))
        (validate-when (comp :fund :reward_params)
          (validate-by [:reward_params :fund]
                       #(contains? (-> env :payment :fund keys set) %) :message "Данного фонда не существует")))
      (presence-of :address :message "Укажите адрес")
      (validate-some
        (presence-of :area_id :message "Указана неправильная область")
        (existence-of :area_id :area :message "Указанная область недействительна"))
      (validate-some
        (presence-of :district_id :message "Указан неправильный район")
        (existence-of :district_id :district :message "Указанный район недействителен"))
      (validate-some
        (presence-of :lng :message "Укажите долготу")
        (numericality-of :lng :gte 0 :message "Долгода неверна"))
      (validate-some
        (presence-of :lat :message "Укажите широту")
        (numericality-of :lat :gte 0 :message "Широта неверна"))
      ; Validate incident time only for non enterprise users
      (validate-when (complement :is_enterprise)
        (validate-some
          (presence-of :incident_time :message "Укажите время")
          (presence-of :incident_date :message "Дата указана неверно")
          (validate-by :incident_date_time
                       #(.isAfter % (.minusHours (util/now) (* 24 3)))
                       :message "Нарушение должно быть не старше трех дней")))
      (validate-some
        (presence-of :offenses :message "Укажите нарушения")
        (validate-by :offenses #(and (map? %) (seq %)) :message "Укажите нарушения")
        (validate-by :offenses #(<= (count %) 15) :message "Вы можете добавить до 15 нарушений")
        (validate-vals-of :offenses
          (presence-of :vehicle_id :message "Укажите номер машины")
          (validate-some
            (presence-of :type_id :message "Укажите тип нарушения")
            (existence-of :type_id :offense_type :message "Указанный тип недействителен")
            (validate-when (comp :show_details :type)
              (presence-of :testimony :message "Для выбранного type_id требуется указать описание нарушения")))
          (validate-when :vehicle_has_country_code
            (presence-of :vehicle_country_code :message "Укажите код страны"))
          (validate-when :citizen_article_id
            (existence-of :citizen_article_id :article :message "Указанная статья не существует"))))
      (validate-if :async
        (validate-all
          (validate-some
            (presence-of :video_id :message "Укажите видео")
            (temp-video-of :video_id))
          (validate-if :with_extra_video
            (validate-some
              (presence-of :extra_video_type :message "Укажите тип видео")
              (inclusion-of :extra_video_type :in #{"remake" "sequel" "rear"} :message "Укажите тип видео")
              (presence-of :extra_video_id :message "Укажите видео")
              (temp-video-of :extra_video_id))
            (absence-of :extra_video_id)))
        (validate-all
          (absence-of :video_id)
          (absence-of :extra_video_id)
          (validate-some
            (presence-of :video :message "Укажите видео")
            (is-valid-video-of :video))
          (validate-if :with_extra_video
            (validate-some
              (presence-of :extra_video :message "Укажите видео")
              (is-valid-video-of :extra_video))
            (absence-of :extra_video)))))))


(def validate-update-report
  (nillify
    (validation-set
      (validate-some
        (presence-of :reward_params :message "Выберите вознаграждение")
        (validate-by :reward_params #(= (count (keys %)) 1) :message "Вознаграждение неверно")
        (validity-of :reward_params
                     (all-keys-in (set (map keyword (keys (remove :unavailable spec/reward-types))))
                                  :unknown-message "Вознаграждение неверно"))
        (validate-when (comp :phone :reward_params)
          (is-phone-of [:reward_params :phone]))
        (validate-when (fn [x]
                         (contains? (:reward_params x) :card))
                       (validate-some
                         (is-card-of [:card_number])
                         (is-supported-card-of [:card_number])
                         (is-real-card-of [:card_number])))
        (validate-when (comp :bank :reward_params)
          (is-bank-of [:reward_params :bank]))
        (validate-when (comp :fund :reward_params)
          (validate-by [:reward_params :fund]
                       #(contains? (-> env :payment :fund keys set) %) :message "Данного фонда не существует"))))))

;; has tests
(def validate-oauth-request
  (nillify
    (validation-set
      (validate-some
        (nest-some-validators :invalid_request
          (presence-of :client_id :message "Не указан client_id")
          (presence-of [:stored_client :client_id] :message "Указан не существующий client_id")
          (presence-of :response_type :message "Не указан response_type")
          (validate-when :redirect_uri
            (inclusion-of :redirect_uri :in [:stored_client :redirect_uri]
                          :message "redirect_uri должен быть одни из этих: "))
          (validate-when #(some? (:state %))
            (length-of :state :within (range 0 33))))

        (nest-some-validators :unsupported_response_type
          (validate-by :response_type #(#{"code", "authorization_code"} %)
                       :message "Данный response_type не поддерживается. Поддерживаются: code"))

        (nest-some-validators :invalid_scope
          (subset-of :scope :of [:stored_client :allowed_scope]
                     :message "Предоставленный scope превышает разрешенный клиенту scope"))))))

;; has tests
(def validate-oauth-client-credentials
  (nillify
    (validation-set
      (nest-some-validators :invalid_client
        (validate-some
          (presence-of :client_id :message "Не указан client_id")
          (presence-of :client_secret :message "Не указан client_secret")
          (presence-of [:stored_client :client_secret] :message "Неверные данные client_id или client_secret")
          (validate-by [:stored_client :client_enabled] identity
                       :message "Ваш клиент отключен. Свяжитесь с администратором"))))))

;; has tests
(def validate-oauth-access-token
  (nillify
    (validation-set
      (validate-some
        (nest-some-validators :unsupported_grant_type
          (inclusion-of :grant_type :in #{"code" "client_credentials"}))
        (nest-some-validators :invalid_request
          (presence-of :grant_type :message "Не указан grant_type")
          (validate-when (comp #{"code"} :grant_type :stored_client)
            (presence-of :code :message "Не указан code")))

        (nest-some-validators :invalid_grant
          (validate-when (comp #{"code"} :grant_type :stored_client)
            (validate-some
              (presence-of [:stored_token :code] :message "Предоставленный код не верен или истек")
              (equality-of [:client_id [:stored_token :client_id]]
                           :message "Предоставленный client_id не совпадает")))
          (equality-of [:grant_type [:stored_client :grant_type]] :message "Неверный grant_type")


          (validate-when (comp :redirect_uri :stored_token)
            (validate-some
              (presence-of :redirect_uri
                           :message "Не указан redirect_uri. Эта ошибка не возникает если пользователь не отправляет redirect_uri")
              (equality-of [:redirect_uri [:stored_token :redirect_uri]]
                           :message "Предоставленный redirect_uri не совпадает"))))))))




;; has tests
(def validate-oauth-refresh-token
  (nillify
    (validation-set
      (validate-some
        (nest-some-validators :invalid_request
          (presence-of :grant_type :message "Не указан grant_type")
          (presence-of :refresh_token :message "Не указан refresh_token"))

        (nest-some-validators :unsupported_grant_type
          (validate-by :grant_type #(= "refresh_token" %)
                       :message "Данный grant_type не поддерживается. Поддерживаются: refresh_token"))

        (nest-some-validators :invalid_grant
          (validate-some
            (presence-of [:stored_token :refresh_token] :message "Не найден refresh_token")
            (validate-by [:stored_token :revoked] (comp not identity)
                         :message "Доступ был отозван пользователем. Запросите доступ ещё раз")
            (validate-by [:stored_token :refresh_expire_time] #(.isAfter % (util/now))
                         :message "Истек срок годности refresh_token'а. Запросите доступ ещё раз")
            (validate-by [:stored_token :client_enabled] identity
                         :message "Ваш клиент отключен. Свяжитесь с администратором")))
        (nest-some-validators :invalid_scope
          (subset-of :scope :of [:stored_token :scope]
                     :message "Предоставленный scope превышает scope токена"))))))


;; has tests
(def validate-oauth-scope
  (nillify
    (validation-set
      (nest-some-validators :insufficient_scope
        (superset-of :client_allowed_scope :of :resource_scope
                     :message "scope данного ресурса превышает разрешенный клиенту scope")
        (superset-of :token_scope :of :resource_scope
                     :message "scope данного ресурса превышает scope токена")))))

(comment
  (validate-oauth-scope {:token_scope          ["read-card-phone"],
                         :client_allowed_scope ["send-report" "read-card-phone"],
                         :client_default_scope ["send-report"],
                         :resource_scope       ["send-report"]}))

(def validate-oauth-client-request
  (nillify
    (validation-set
      (validate-some
        (nest-some-validators :invalid_token
          (presence-of :client_id :message "Клиент не найден")
          (validate-by :token_revoked (comp not identity) :message "Доступ был отозван пользователем. Запросите доступ ещё раз")
          (validate-by :token_refresh_expire_time #(.isAfter % (util/now)) :message "Истек срок годности refresh_token'а. Запросите доступ ещё раз")
          (validate-by :token_access_expire_time #(.isAfter % (util/now)) :message "Истек срок годности access_token'а. Обновите с помощью refresh_token'а"))

        (nest-some-validators :invalid_client
          (validate-by :client_enabled identity :message "Ваш клиент отключен. Свяжитесь с администратором"))))))


(def validate-client-form
  (nillify
    (validation-set
      (presence-of :id :message "Укажите ID")
      (validate-when #(or (:creation %) (not= (:old-id %) (:id %)))
        (validate-some
          (uniqueness-of
            :id :oauth_client
            :exclude-column nil
            :message "Такой клиент уже существует")))
      (presence-of :secret :message "Укажите secret")
      (presence-of :name :message "Укажите название клиента")
      (inclusion-of :grant_type :in #{"code" "client_credentials"})
      (validate-when (comp #{"client_credentials"} :grant_type)
        (validate-some
          (presence-of :webhook_login :message "Укажите логин для webhook'а")
          (length-of :webhook_login :within (range 5 33) :message-fn (constantly "Должно быть длиннее 4 символов"))
          (presence-of :webhook_password :message "Укажите пароль для webhook'а")
          (length-of :webhook_password :within (range 8 33) :message-fn (constantly "Должно быть длиннее 7 символов"))))

      (validate-when (comp #{"code"} :grant_type)
        (validate-some
          (presence-of :redirect_uri :message "Укажите перенаправления")
          (validate-by :redirect_uri not-empty :message "Укажите перенаправления")
          (presence-of :error_redirect_uri :message "Укажите адрес перенаправления")
          (validate-by :allowed_scope not-empty :message "Укажите права")
          (validate-by :default_scope not-empty :message "Укажите права")
          (subset-of :default_scope :of :allowed_scope :message "Выбраныне права нарушают разрешенные"))))))


(def validate-create-citizen
  (nillify
    (validation-set
      (validate-some
        (presence-of #{:first_name :last_name} :message "Поле не может быть пустым")
        (length-of :first_name :within (range 3 33) :message-fn (constantly "Должно быть длиннее 3х символов"))
        (length-of :last_name :within (range 3 33) :message-fn (constantly "Должно быть длиннее 3х символов")))
      (validate-some
        (presence-of :phone :message "Укажите телефонный номер")
        (is-phone-of :phone))
      (validate-when #(contains? % :card)
        (validate-some
          (is-card-of :card)
          (is-supported-card-of :card)
          (is-real-card-of :card)))
      (validate-when #(contains? % :area_id)
        (validate-some
          (presence-of :area_id)
          (existence-of :area_id :area)))
      (validate-when #(contains? % :district_id)
        (validate-some
          (presence-of :district_id)
          (existence-of :district_id :district))))))
