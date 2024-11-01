(ns jarima.spec
  (:require [cuerdas.core :as str]
            [jarima.util :as util]
            [spec-tools.core :as st]
            [clojure.spec.alpha :as s]
            [medley.core :refer [uuid]]
            [buddy.core.codecs.base64 :as base64]
            [reitit.ring.middleware.multipart :as multipart]))


(s/def ::blank-string
  (s/and string? str/blank?))


(s/def ::nillable-uuid
  (st/spec
    {:spec          (s/nilable (s/or :blank ::blank-string :default uuid?))
     :decode/string (fn [_ x]
                      (when-not (str/blank? x)
                        (try (uuid x)
                             (catch Exception _ x))))}))


(s/def ::nillable-csv-string
  (st/spec {:decode/string (fn [_ x]
                             (when (and (some? x) (string? x))
                               (->> (str/split x #",")
                                    (map str/trim)
                                    (filter not-empty)
                                    (vec))))}))




(s/def ::uuid
  (st/spec
    {:spec          uuid?
     :decode/string (fn [_ x]
                      (try (uuid x)
                           (catch Exception _ x)))}))


(s/def ::nillable-int
  (st/spec
    {:spec          (s/nilable int?)
     :decode/string (fn [_ x]
                      (when-not (str/blank? x)
                        (try (Long/parseLong x)
                             (catch Exception _ x))))}))


(s/def ::nillable-point-radius
  (st/spec
    {:spec          (s/nilable any?)
     :decode/string (fn [_ x]
                      (when-not (or (str/blank? x) (< (count (str/split x ",")) 2))
                        (try (map #(Double/parseDouble (str/replace % #" " "")) (str/split x ","))
                             (catch Exception _ x))))}))


(s/def ::nillable-clean-int
  (st/spec
    {:spec          (s/nilable int?)
     :decode/string (fn [_ x]
                      (when-not (str/blank? x)
                        (try (Long/parseLong (str/replace x #" " ""))
                             (catch Exception _ x))))}))


(s/def ::nillable-double
  (st/spec
    {:spec          (s/nilable double?)
     :decode/string (fn [_ x]
                      (when-not (str/blank? x)
                        (try (Double/parseDouble x)
                             (catch Exception _ x))))}))


(s/def ::nillable-clean-double
  (st/spec
    {:spec          (s/nilable double?)
     :decode/string (fn [_ x]
                      (when-not (str/blank? x)
                        (try (Double/parseDouble (str/replace x #" " ""))
                             (catch Exception _ x))))}))


(s/def ::nillable-local-date
  (st/spec
    {:spec          (s/nilable util/local-date?)
     :decode/string (fn [_ x]
                      (when-not (str/blank? x)
                        (try (util/parse-local-date-short x)
                             (catch Exception _ x))))}))


(s/def ::nillable-local-time
  (st/spec
    {:spec          (s/nilable util/local-time?)
     :decode/string (fn [_ x]
                      (when-not (str/blank? x)
                        (try (util/parse-local-time-short x)
                             (catch Exception _ x))))}))


(s/def ::boolean
  (st/spec
    {:spec          boolean?
     :decode/string (fn [_ x]
                      (contains? #{"on" "true" "1" "yes" true 1} x))}))


(s/def ::nillable-string
  (st/spec
    {:spec          (s/nilable string?)
     :decode/string (fn [_ x]
                      (not-empty x))}))


(s/def ::nillable-clean-string
  (st/spec
    {:spec          (s/nilable string?)
     :decode/string (fn [_ x]
                      (when (not-empty x)
                        (when (not-empty x)
                          (if (re-find #"\d+" x)
                            (str/replace x #"[\s+-]" "")
                            (str/clean x)))))}))


(s/def ::jpg-data-url
  (st/spec
    {:spec          (s/nilable bytes?)
     :decode/string (fn [_ x]
                      (when (string? x)
                        (when-let [[head body] (str/split x #"," 2)]
                          (when (re-matches #"data:(image/(jpeg|jpg));base64" head)
                            (base64/decode body)))))}))

(s/def :entity/offenses (s/map-of any? :entity/offense))


(def offense-statuses
  {"created"   {:name "Ожидание рассмотрения" :color "brand"}
   "rejected"  {:name "Отклонен" :color "danger"}
   "accepted"  {:name "Рассмотрен" :color "success"}
   "failed"    {:name "Не доставлен" :color "warning"}
   "forwarded" {:name "Ожидание оплаты" :color "success"}
   "dismissed" {:name "Отклонён судом" :color "danger"}
   "paid"      {:name "Выплачен" :color "success"}})

(def encoder-statuses
  {"created"  {:name "В очереди" :color "brand"}
   "started"  {:name "В процессе" :color "info"}
   "finished" {:name "Завершён" :color "success"}
   "failed"   {:name "Ошибка" :color "danger"}
   "canceled" {:name "Отменён" :color "warning"}})

(def detector-statuses
  {"created"   {:name "В процессе" :color "brand"}
   "succeeded" {:name "Завершён" :color "success"}
   "failed"    {:name "Ошибка" :color "danger"}})

(def report-statuses
  {"created"  {:name "Ожидание рассмотрения" :color "brand"}
   "reviewed" {:name "Рассмотрен" :color "success"}})


(def oauth-grant-types
  {"code"               {:name        "Code"
                         :description "Данный клиент сможет получить доступ к данным, только если гражданин разрешит"}
   "client_credentials" {:name        "Client credentials"
                         :description "Данный клиент сможет получить доступ к данным всех пользователей"}
   "refresh_token"      {:name   "Refresh token"
                         :hidden true}})


(def reward-statuses
  {"created" {:name "В ожидании" :color "brand"}
   "paid"    {:name "Выплачен" :color "success"}
   "ignored" {:name "Проигнорирован" :color "success"}
   "failed"  {:name "Ошибка" :color "danger"}})


(def transfer-statuses
  {"created" {:name "Оформлено" :color "brand"}
   "sent"    {:name "Отправлен" :color "success"}})


(def reward-types
  {"phone"     {:name        "Пополнение мобильного счета"
                :description "Доступны операторы: Beeline, Ucell, UzMobile, UMS, Perfectum Mobile. Юридические номера телефонов не принимаются. Операттор HUMANS не принимается."
                :icon        "\uD83D\uDCF2"}
   "fund"      {:name        "Благотворительное пожертвование"
                :description "Вознаграждение будет отправлено на счета выбранного благотворительного фонда. Выбор пожертвовать будет показано нарушителю."
                :icon        "\uD83D\uDE07"}
   "card"      {:name        "Перевод на карту"
                :description "Перевод денег на выбранную вами Uzcard карту. Срок действия карты не должен заканчиваться раньше двух месяцев."
                :icon        "\uD83D\uDCB3"}
   "no-reward" {:name        "Без вознаграждения"
                :description "Поступившие вознаграждение будут отправлены инспектору рассмотревший видео. Факт отказа от вознаграждения будет показан нарушителю"
                :icon        "\uD83D\uDE24"}
   "bank"      {:name        "Банковский перевод"
                :description "Банковский денежный перевод на расчетный счет выбранного юридического лица."
                :icon        "\uD83C\uDFE6"}})


(def video-types
  {"rear"   {:name "Задняя камера"}
   "remake" {:name "Нарушение правил остановки и стоянки"}
   "sequel" {:name "Продолжение"}})


(s/def :oauth/grant_type
  (st/spec
    {:spec          (s/nilable (-> oauth-grant-types keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def :transfer/status
  (st/spec
    {:spec          (s/nilable (-> transfer-statuses keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def :report/status
  (st/spec
    {:spec          (s/nilable (-> report-statuses keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def :offense/status
  (st/spec
    {:spec          (s/nilable (-> offense-statuses keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def :reward/status
  (st/spec
    {:spec          (s/nilable (-> reward-statuses keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def :reward/type
  (st/spec
    {:spec          (s/nilable (-> reward-types keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def :video/type
  (st/spec
    {:spec          (s/nilable (-> video-types keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def ::open-right-date-range
  (st/spec
    {:spec          any?
     :decode/string (fn [_ x]
                      (util/mute
                        (-> x
                            util/to-date-range
                            util/to-open-right-date-range)))}))


(def roles
  {"admin"     "Администратор"
   "inspector" "Инспектор"})


(s/def :filter/role
  (st/spec
    {:spec          (s/nilable (-> roles keys set))
     :decode/string (fn [_ x] (not-empty x))}))


(s/def ::multipart-file (s/nilable multipart/temp-file-part))

(s/def :filter.report/sort #{"create_time_asc" "create_time_desc"})
(s/def :filter.offense/sort #{"create_time_asc" "create_time_desc"})

(s/def :filter/page ::nillable-int)
(s/def :filter/size ::nillable-int)
(s/def :filter.transfer/status :transfer/status)
(s/def :filter.report/status :report/status)
(s/def :filter.offense/status :offense/status)
(s/def :filter.reward/status :reward/status)
(s/def :filter.reward/type :reward/type)
(s/def :filter/number ::nillable-int)
(s/def :filter/point_radius ::nillable-point-radius)
(s/def :filter/citizen_id ::nillable-uuid)
(s/def :filter/staff_id ::nillable-uuid)
(s/def :filter/exclude_report_id ::nillable-uuid)
(s/def :filter/area_id ::nillable-uuid)
(s/def :filter/district_id ::nillable-uuid)
(s/def :filter/article_id ::nillable-int)
(s/def :filter/type_id ::nillable-int)
(s/def :filter/article_ids ::nillable-csv-string)
(s/def :filter/response_id ::nillable-uuid)
(s/def :filter/inspector_id ::nillable-uuid)
(s/def :filter/vehicle_id ::nillable-string)
(s/def :filter/bank_account ::nillable-string)
(s/def :filter/failure_message ::nillable-string)
(s/def :filter/founder_role ::nillable-string)
(s/def :filter/reward_type ::nillable-string)
(s/def :filter/receiver_role ::nillable-string)
(s/def :filter/name ::nillable-string)
(s/def :filter/create_time ::open-right-date-range)
(s/def :filter/incident_time ::open-right-date-range)
(s/def :filter/date_range ::open-right-date-range)
(s/def :filter/version ::nillable-int)
(s/def :filter/q ::nillable-clean-string)


(s/def :sms/code ::nillable-string)


(s/def :entity/id ::nillable-uuid)
(s/def :entity/name ::nillable-string)
(s/def :entity/name_ru ::nillable-string)
(s/def :entity/obsolete ::boolean)
(s/def :entity/citizen_selection_enabled ::boolean)
(s/def :entity/only_my ::boolean)
(s/def :entity/name_uz_cy ::nillable-string)
(s/def :entity/name_uz_la ::nillable-string)
(s/def :entity/show_details ::boolean)
(s/def :entity/code int?)
(s/def :entity/priority int?)
(s/def :entity/url ::nillable-string)
(s/def :entity/yname_ru ::nillable-string)
(s/def :entity/yname_uz_cy ::nillable-string)
(s/def :entity/yname_uz_la ::nillable-string)
(s/def :entity/text_ru ::nillable-string)
(s/def :entity/text_uz_cy ::nillable-string)
(s/def :entity/text_uz_la ::nillable-string)
(s/def :entity/alias_ru ::nillable-string)
(s/def :entity/alias_uz_cy ::nillable-string)
(s/def :entity/alias_uz_la ::nillable-string)
(s/def :entity/citizen_alias_ru ::nillable-string)
(s/def :entity/citizen_alias_uz_cy ::nillable-string)
(s/def :entity/citizen_alias_uz_la ::nillable-string)
(s/def :entity/category_ru ::nillable-string)
(s/def :entity/category_uz_cy ::nillable-string)
(s/def :entity/category_uz_la ::nillable-string)
(s/def :entity/question_ru ::nillable-string)
(s/def :entity/question_uz_cy ::nillable-string)
(s/def :entity/question_uz_la ::nillable-string)
(s/def :entity/answer_ru ::nillable-string)
(s/def :entity/answer_uz_cy ::nillable-string)
(s/def :entity/answer_uz_la ::nillable-string)
(s/def :entity/area_id ::nillable-uuid)
(s/def :entity/video_id ::nillable-string)
(s/def :entity/extra_video_type :video/type)
(s/def :entity/extra_video_id ::nillable-string)
(s/def :entity/district_id ::nillable-uuid)
(s/def :entity/card_bank_id ::nillable-uuid)
(s/def :entity/role (s/nilable (-> roles keys set)))
(s/def :entity/phone ::nillable-clean-string)
(s/def :entity/public_phone ::nillable-clean-string)
(s/def :entity/second_phone ::nillable-clean-string)
(s/def :entity/username ::nillable-string)
(s/def :entity/password ::nillable-string)
(s/def :entity/old_password ::nillable-string)
(s/def :entity/new_password ::nillable-string)
(s/def :entity/repeated_new_password ::nillable-string)
(s/def :entity/first_name ::nillable-string)
(s/def :entity/last_name ::nillable-string)
(s/def :entity/middle_name ::nillable-string)
(s/def :entity/email ::nillable-string)
(s/def :entity/address ::nillable-string)
(s/def :entity/zipcode ::nillable-string)
(s/def :entity/rank ::nillable-string)
(s/def :entity/card ::nillable-clean-string)
(s/def :reward-params/card ::boolean)
(s/def :entity/bank ::nillable-clean-string)
(s/def :entity/type_id ::nillable-int)
(s/def :entity/area_id ::nillable-uuid)
(s/def :entity/district_id ::nillable-uuid)
(s/def :entity/lat ::nillable-double)
(s/def :entity/lng ::nillable-double)
(s/def :entity/incident_date ::nillable-local-date)
(s/def :entity/incident_time ::nillable-local-time)
(s/def :entity/vehicle_id ::nillable-clean-string)
(s/def :entity/testimony ::nillable-string)
(s/def :entity/fund ::nillable-string)
(s/def :entity/no-reward ::boolean)
(s/def :entity/with_extra_video ::boolean)
(s/def :entity/active ::boolean)
(s/def :entity/upload_forbidden ::boolean)
(s/def :entity/review_allowed ::boolean)
(s/def :entity/two_factor_enabled ::boolean)
(s/def :entity/video ::multipart-file)
(s/def :entity/extra_video ::multipart-file)
(s/def :entity/response_id ::nillable-uuid)
(s/def :entity/article_id ::nillable-int)
(s/def :entity/citizen_article_id ::nillable-int)
(s/def :entity/extra_response ::nillable-string)
(s/def :entity/vehicle_img ::jpg-data-url)
(s/def :entity/vehicle_id_img ::jpg-data-url)
(s/def :entity/extra_img ::jpg-data-url)
(s/def :entity/factor ::nillable-clean-double)
(s/def :mwage/value ::nillable-clean-int)
(s/def :entity/message ::nillable-string)

(s/def :entity/offense
  (s/keys
    :opt-un [:entity/vehicle_id
             :entity/citizen_article_id
             :entity/type_id
             :entity/testimony]))


(s/def :article/number ::nillable-string)
(s/def :article/id ::nillable-int)
(s/def :offense-type/id ::nillable-int)


(s/def :reward/params
  (s/keys
    :opt-un [:entity/phone
             :reward-params/card
             :entity/fund
             :entity/bank
             :entity/no-reward]))

(s/def :entity/reward_params :reward/params)


(s/def :asbt/notify
  (s/keys
    :opt-un [:entity/pId]))


(s/def :admin.staff.index/query
  (s/keys
    :opt-un [:filter/role
             :filter/area_id
             :filter/district_id
             :filter/name
             :filter/page]))


(s/def :admin.citizen.index/query
  (s/keys
    :opt-un [:filter/area_id
             :filter/district_id
             :filter/q
             :filter/page]))


(s/def :staff.report.index/query
  (s/keys
    :opt-un [:filter/citizen_id
             :filter/area_id
             :filter/district_id
             :filter/create_time
             :filter/number
             :filter/point_radius
             :filter.report/status
             :filter/page
             :filter.report/sort]))


(s/def :staff.statistics/date_range
  (s/keys
    :req-un [:filter/date_range]
    :opt-un [:filter/reward_type]))


(s/def :staff.offense_points/query
  (s/keys
    :req-un [:filter/date_range]
    :opt-un [:filter/article_ids]))


(s/def :staff.statistics/date_range_and_area_id
  (s/keys
    :req-un [:filter/date_range]
    :opt-un [:filter/area_id]))


(s/def :staff.report.view/query
  (s/keys
    :opt-un [:filter/version]))


(s/def :staff.report.citizen/query
  (s/keys
    :opt-un [:filter/q]))


(s/def :inspector.report.vehicle/query
  (s/keys
    :opt-un [:filter/vehicle_id
             :filter/exclude_report_id
             :filter/incident_time]))


(s/def :staff.offense.index/query
  (s/keys
    :opt-un [:filter/vehicle_id
             :filter/exclude_report_id
             :filter/area_id
             :filter/type_id
             :filter/incident_time
             :filter/district_id
             :filter/failure_message
             :filter/article_id
             :entity/only_my
             :entity/founder_role
             :filter/response_id
             :filter/inspector_id
             :filter.offense/status
             :filter/create_time
             :filter/number
             :filter.offense/sort
             :filter/page]))


(s/def :citizen.offense.index/query
  (s/keys
    :opt-un [:filter/vehicle_id
             :filter/area_id
             :filter/type_id
             :filter/incident_time
             :filter/district_id
             :filter/article_id
             :filter.offense/status
             :filter/create_time
             :filter/number
             :filter.offense/sort
             :filter/page]))


(s/def :citizen.report.index/query
  (s/keys
    :opt-un [:filter.report/status
             :filter/page]))


(s/def :admin.area/form
  (s/keys
    :opt-un [:entity/name_ru
             :entity/name_uz_cy
             :entity/name_uz_la
             :entity/code
             :entity/obsolete
             :entity/yname_ru
             :entity/yname_uz_cy
             :entity/yname_uz_la]))


(s/def :admin.district/form
  (s/keys
    :opt-un [:entity/area_id
             :entity/name_ru
             :entity/name_uz_cy
             :entity/name_uz_la
             :entity/code
             :entity/obsolete
             :entity/yname_ru
             :entity/yname_uz_cy
             :entity/yname_uz_la]))


(s/def :admin.article/form
  (s/keys
    :opt-un [:article/id
             :article/number
             :entity/url
             :entity/obsolete
             :entity/factor
             :entity/text_ru
             :entity/text_uz_cy
             :entity/text_uz_la
             :entity/alias_ru
             :entity/alias_uz_cy
             :entity/alias_uz_la
             :entity/citizen_selection_enabled
             :entity/citizen_alias_ru
             :entity/citizen_alias_uz_cy
             :entity/citizen_alias_uz_la]))


(s/def :admin.offense-type/form
  (s/keys
    :opt-un [:article/id
             :entity/name_ru
             :entity/name_uz_cy
             :entity/show_details
             :entity/name_uz_la]))


(s/def :admin.faq/form
  (s/keys
    :opt-un [:entity/category_ru
             :entity/category_uz_cy
             :entity/category_uz_la
             :entity/question_ru
             :entity/question_uz_cy
             :entity/question_uz_la
             :entity/answer_ru
             :entity/answer_uz_cy
             :entity/answer_uz_la]))


(s/def :staff.response/form
  (s/keys
    :opt-un [:entity/priority
             :entity/obsolete
             :entity/text_ru
             :entity/text_uz_cy
             :entity/text_uz_la
             :entity/alias_ru
             :entity/alias_uz_cy
             :entity/alias_uz_la]))


(s/def :admin.staff/form
  (s/keys
    :opt-un [:entity/role
             :entity/username
             :entity/password
             :entity/first_name
             :entity/last_name
             :entity/middle_name
             :entity/phone
             :entity/public_phone
             :entity/rank
             :entity/active
             :entity/review_allowed
             :entity/two_factor_enabled
             :entity/area_id
             :entity/district_id]))


(s/def :admin.citizen/form
  (s/keys
    :opt-un [:entity/phone
             :entity/email
             :entity/area_id
             :entity/zipcode
             :entity/address
             :entity/upload_forbidden
             :entity/new_password
             :entity/has_password
             :entity/last_name
             :entity/first_name
             :entity/middle_name
             :entity/district_id
             :entity/second_phone]))


(s/def :citizen.report/form
  (s/keys
    :opt-un [:entity/address
             :entity/area_id
             :entity/district_id
             :entity/lat
             :entity/lng
             :entity/incident_date
             :entity/incident_time
             :entity/offenses
             :entity/video_id
             :entity/extra_video_id
             :entity/extra_video_type
             :entity/video
             :entity/extra_video
             :entity/with_extra_video
             :entity/reward_params]))


(s/def :staff.reward/form
  (s/keys
    :opt-un [:entity/card]))


(s/def :citizen.report/upload-video
  (s/keys
    :opt-un [:entity/video]))


(s/def :service/sms
  (s/keys
    :opt-un [:entity/message
             :entity/phone]))


(s/def :citizen.profile/form
  (s/keys
    :opt-un [:entity/first_name
             :entity/middle_name
             :entity/last_name
             :entity/email
             :entity/second_phone
             :entity/area_id
             :entity/district_id
             :entity/address
             :entity/zipcode
             :entity/card
             :entity/card_bank_id]))


(s/def :misc.register/form
  (s/keys
    :opt-un [:entity/first_name
             :entity/middle_name
             :entity/last_name
             :entity/email
             :entity/phone
             :entity/second_phone
             :entity/area_id
             :entity/district_id
             :entity/address
             :entity/zipcode
             :entity/card
             :entity/card_bank_id]))


(s/def :misc.send-code/form
  (s/keys
    :opt-un [:entity/phone]))


(s/def :misc.staff-send-code/form
  (s/keys
    :opt-un [:entity/username]))


(s/def :misc.verify-code/form
  (s/keys
    :opt-un [:entity/phone
             :sms/code]))


(s/def :misc.staff-verify-code/form
  (s/keys
    :opt-un [:entity/username
             :sms/code]))


(s/def :misc.login/form
  (s/keys
    :opt-un [:entity/phone
             :entity/password]))


(s/def :misc.verify-card/form
  (s/keys
    :opt-un [:entity/card]))


(s/def :misc.change-password/form
  (s/keys
    :opt-un [:entity/old_password
             :entity/new_password
             :entity/repeated_new_password]))


(s/def :misc.search/query
  (s/keys
    :opt-un [:filter/q]))


(comment
  (st/conform :citizen.report.index/query {:status "created"} st/string-transformer)
  (st/conform :staff.report.index/query {:create_time "28.06.2019-28.06.2019"} st/string-transformer)
  (st/conform :admin.reward.repay/form {:type "phone", :params {:phone "+998 97 442 99 82"}} st/string-transformer)
  (st/conform ::nillable-double "123" st/string-transformer)
  (st/conform ::nillable-string "1" st/string-transformer)
  (st/conform ::nillable-local-date "31.12.2007" st/string-transformer)
  (st/conform ::nillable-local-time "1:01" st/string-transformer)
  (st/conform :filter.report/sort "create_time_asc" st/string-transformer)
  (st/conform ::nillable-clean-string "14 va 41-31" st/string-transformer))


(s/def :admin.audit.index/query
  (s/keys
    :opt-un [:filter/page]))


(s/def :admin.config.mwage/form
  (s/keys
    :opt-un [:mwage/value]))


(s/def :admin.reward.index/query
  (s/keys
    :opt-un [:filter/area_id
             :filter/district_id
             :filter/create_time
             :filter/citizen_id
             :filter/receiver_role
             :filter/staff_id
             :filter/number
             :filter.reward/status
             :filter.reward/type
             :filter/failure_message
             :filter/page]))


(s/def :admin.reward.repay/form
  (s/keys
    :opt-un [:reward/params]))


(s/def :review/offense
  (s/keys
    :opt-un [:entity/id
             :filter.offense/status
             :entity/vehicle_id
             :entity/response_id
             :entity/article_id
             :entity/extra_response
             :entity/vehicle_img
             :entity/vehicle_id_img
             :entity/extra_img]))


(s/def :review/offenses (s/map-of any? :review/offense))


(s/def :staff.report/form
  (s/keys
    :opt-un [:entity/address
             :entity/district_id
             :review/offenses]))


(def oauth-scopes
  {"citizen"   {"send-report"       {:name        "Отправление заявок",
                                     :description "Приложение сможет создавать заявки от вашего аккаунта"
                                     :icon        "la la-upload"}
                "read-card-phone"   {:name        "Uzcard  карту и телефон профиля",
                                     :description "Приложение сможет узнать ваш телефон и карту",
                                     :icon        "la la-user"
                                     :columns     [:card :phone]}
                "read-organization" {:name        "Данные Юрлица",
                                     :description "Приложение сможет узнать только название и банковский аккаунт",
                                     :icon        "la la-user"
                                     :columns     [:name :bank_account]}}

   "inspector" {:read-inspector {:name        "Данные профиля",
                                 :description "Приложение сможет узнать ваш email, телефон и имя",
                                 :icon        "la la-user"}}})




(s/def :entity/redirect_uri ::nillable-csv-string)


(s/def :entity/offense_status_webhook ::nillable-string)
(s/def :entity/webhook_login ::nillable-string)
(s/def :entity/webhook_password ::nillable-string)
(s/def :entity/report_status_webhook ::nillable-string)
(s/def :entity/secret ::nillable-string)
(s/def :entity/enabled ::boolean)
(s/def :entity/encoding_required ::boolean)
(s/def :entity/has_password ::boolean)
(s/def :entity/url ::nillable-string)
(s/def :entity/logo ::nillable-string)
(s/def :entity/error_redirect_uri ::nillable-string)
(s/def :entity/allowed_scope ::nillable-csv-string)
(s/def :entity/default_scope ::nillable-csv-string)

(s/def :oauth/scope ::nillable-csv-string)

(s/def :oauth/login ::boolean)
(s/def :oauth/debug ::boolean)
(s/def :oauth/state ::nillable-string)
(s/def :oauth/client_id ::nillable-uuid)
(s/def :oauth/token_id ::nillable-uuid)
(s/def :oauth/refresh_token ::nillable-string)
(s/def :oauth/redirect_uri ::nillable-string)
(s/def :oauth/response_type ::nillable-string)
(s/def :oauth/client_secret ::nillable-string)


(s/def :admin.client/form
  (s/keys
    :opt-un [:entity/url
             :entity/id
             :entity/logo
             :entity/name
             :entity/secret
             :oauth/grant_type
             :entity/redirect_uri
             :entity/error_redirect_uri
             :entity/allowed_scope
             :entity/default_scope
             :entity/offense_status_webhook
             :entity/webhook_login
             :entity/webhook_password
             :entity/report_status_webhook
             :entity/enabled
             :entity/encoding_required]))


(s/def :oauth.authorize/query
  (s/keys
    :opt-un [:oauth/debug
             :oauth/scope
             :oauth/state
             :oauth/login
             :oauth/client_id
             :oauth/redirect_uri
             :oauth/response_type]))


(s/def :oauth.authorize/form
  (s/keys
    :opt-un [:oauth/scope
             :oauth/state
             :oauth/client_id
             :oauth/redirect_uri
             :oauth/response_type]))


(s/def :oauth.access_token/form
  (s/keys
    :opt-un [:oauth/code
             :oauth/grant_type
             :oauth/redirect_uri
             :oauth/client_id
             :oauth/client_secret]))


(s/def :oauth.refresh_token/form
  (s/keys
    :opt-un [:oauth/refresh_token
             :oauth/grant_type
             :oauth/scope
             :oauth/client_id
             :oauth/client_secret]))


(s/def :oauth.token/revoke
  (s/keys
    :opt-un [:oauth/token_id]))


(s/def :admin.transfer.index/query
  (s/keys
    :opt-un [:filter.transfer/status
             :filter/bank_account
             :filter/create_time
             :filter/number
             :filter/page]))


(s/def :api.citizen.list/query
  (s/keys
    :opt-un [:filter/page
             :filter/size]))
