(ns jarima.db.query
  (:refer-clojure :exclude [update select format partition-by])
  (:require [jarima.util :as util]
            [cuerdas.core :as str]
            [honeysql.core :as sql]
            [jarima.db.core :as db]
            [honeysql.format :refer :all]
            [honeysql.helpers :refer :all]
            [jarima.db.honeysql :refer :all]
            [honeysql-postgres.format :refer :all]
            [medley.core :refer [update-existing]]
            [honeysql-postgres.helpers :refer :all]))


(defn for-update
  ([sql-map]
   (for-update sql-map nil))
  ([sql-map table & flags]
   (apply lock sql-map :mode :update :table table flags)))


(defn count-when
  ([condition]
   (sql/call :sum
     (sql/call :case
       condition 1
       :else 0)))
  ([column condition]
   (sql/call :count
     (sql/call :case
       condition column
       :else nil))))


(defn sum-when [column condition]
  (sql/call :sum
    (sql/call :case
      condition column
      :else 0)))


(defn column
  "Usage: (let [table :area]
            (column table :_id)) => :area_id"
  [& parts]
  (keyword (str/join (map #(if (keyword? %) (name %) %) parts))))


(defn count-value
  "Count rows in the table by given inclusion and exclusion rules"
  [table inclusion exclusion]
  (-> (select :%count.*)
      (from table)
      (where
        (let [[column value] inclusion]
          (if (coll? value)
            [:in column value]
            [:= column value])))
      (merge-where
        (when (seq exclusion)
          (cons :!= (seq exclusion))))))


(defn where-report
  [sql-map {:keys [id exclude_report_id point_radius citizen_id number area_id district_id status create_time age creator_client_id incident_time]}]
  (let [[lat lng radius] point_radius]
    (-> sql-map
        (merge-where (when exclude_report_id [:not [:uuid-in :report.id exclude_report_id]]))
        (merge-where (when id [:uuid-in :report.id id]))
        (merge-where (when number [:int-in :report.number number]))
        (merge-where (when citizen_id [:uuid-in :report.citizen_id citizen_id]))
        (merge-where (when creator_client_id [:uuid-in :report.creator_client_id creator_client_id]))
        (merge-where (when area_id [:uuid-in :report.area_id area_id]))
        (merge-where (when district_id [:uuid-in :report.district_id district_id]))
        (merge-where (when status [:str-in :report.status status]))
        (merge-where (when create_time [:within :report.create_time create_time]))
        (merge-where (when incident_time [:within :report.incident_time incident_time]))
        (merge-where (when point_radius [:< (sql/call :calculate_distance :report.lat :report.lng lat lng "K") (or radius 2)]))
        (merge-where (when age [:within [:diff-days [:cast (util/today) :date] :report.create_time] age])))))


(defn select-report
  ([filter]
   (select-report filter nil))
  ([filter pagination]
   (-> (select :report.*)
       (from :report)
       (where-report filter)
       (apply-pagination pagination)
       (order-by (case (:sort filter)
                   "create_time_asc" [:create_time :asc]
                   "create_time_desc" [:create_time :desc]
                   [:create_time :desc])))))


(defn insert-report
  [& reports]
  (-> (insert-into :report)
      (returning :*)
      (values (map #(clojure.core/update % :reward_params db/raw-value) reports))))


(defn update-report
  [id report]
  (-> (update :report)
      (returning :*)
      (where [:= :id id])
      (sset (update-existing report :reward_params db/raw-value))))


(defn prepare-q [q]
  (str/trim (str/replace (str/lower q) #"['\"`]" "")))


(defn where-citizen
  [sql-map {:keys [id phone q area_id district_id]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :citizen.id id]))
      (merge-where (when phone [:str-in :citizen.phone phone]))
      (merge-where (when area_id [:uuid-in :citizen.area_id area_id]))
      (merge-where (when district_id [:uuid-in :citizen.district_id district_id]))
      (merge-where (when q [:or
                            (sql/call "@@" :citizen.document (util/convert-to-cyr (prepare-q q)))
                            (sql/call "@@" :citizen.document (util/convert-to-lat (prepare-q q)))]))))


(defn select-citizen
  ([filter]
   (select-citizen filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :citizen)
       (where-citizen filter)
       (order-by [:create_time :desc])
       (apply-pagination pagination))))


(defn insert-citizen
  [& citizens]
  (-> (insert-into :citizen)
      (returning :*)
      (values citizens)))


(defn update-citizen
  [filter citizen]
  (-> (update :citizen)
      (returning :*)
      (where-citizen filter)
      (sset citizen)))


(defn where-offense
  [sql-map {:keys [id type_id founder_role incident_time citizen_id fine_id fine_date reject_time dismiss_time pay_time report_id exclude_report_id create_time vehicle_id status accept_time number area_id district_id article_id response_id inspector_id reward_id failure_message creator_client_id creator_staff_id]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :offense.id id]))
      (merge-where (when report_id [:uuid-in :offense.report_id report_id]))
      (merge-where (when exclude_report_id [:not [:uuid-in :offense.report_id exclude_report_id]]))
      (merge-where (when status [:str-in :offense.status status]))
      (merge-where (when fine_id [:str-in :offense.fine_id fine_id]))
      (merge-where (when failure_message [:str-in :offense.failure_message failure_message]))
      (merge-where (when number [:int-in :offense.number number]))
      (merge-where (when area_id [:uuid-in :report.area_id area_id]))
      (merge-where (when district_id [:uuid-in :report.district_id district_id]))
      (merge-where (when article_id [:in :offense.article_id article_id]))
      (merge-where (when reward_id [:in :offense.reward_id reward_id]))
      (merge-where (when response_id [:uuid-in :offense.response_id response_id]))
      (merge-where (when inspector_id [:uuid-in :report.inspector_id inspector_id]))
      (merge-where (when create_time [:within :offense.create_time create_time]))
      (merge-where (when incident_time [:within :report.incident_time incident_time]))
      (merge-where (when type_id (if (and (= 1 (count type_id)) (> 0 (first type_id)))
                                   [:= :offense.type_id nil]
                                   [:in :offense.type_id type_id])))
      (merge-where (when accept_time [:within :offense.accept_time accept_time]))
      (merge-where (when pay_time [:within :offense.pay_time pay_time]))
      (merge-where (when dismiss_time [:within :offense.dismiss_time dismiss_time]))
      (merge-where (when fine_date [:within :offense.fine_date fine_date]))
      (merge-where (when reject_time [:within :offense.reject_time reject_time]))
      (merge-where (when vehicle_id (if (util/valid-regex vehicle_id)
                                      [:re-matches :offense.vehicle_id vehicle_id]
                                      [:= [:upper :offense.vehicle_id] [:upper vehicle_id]])))
      (merge-where (when creator_client_id [:uuid-in :offense.creator_client_id creator_client_id]))
      (merge-where (when citizen_id [:uuid-in :report.citizen_id citizen_id]))
      (merge-where (when (= founder_role "inspector") [:!= :offense.creator_staff_id nil]))
      (merge-where (when (= founder_role "citizen") [:= :offense.creator_staff_id nil]))
      (merge-where (when creator_staff_id [:uuid-in :offense.creator_staff_id creator_staff_id]))))



(defn select-offense
  ([filter]
   (select-offense filter nil))
  ([filter pagination]
   (-> (select :offense.*
               [:report.reward_params :reward_params]
               [:response.text_uz_cy :response_text_uz_cy]
               [:response.text_uz_la :response_text_uz_la]
               [:response.text_ru :response_text_ru]
               [:article.text_uz_cy :article_text_uz_cy]
               [:article.text_uz_la :article_text_uz_la]
               [:article.text_ru :article_text_ru]
               [:article.alias_uz_cy :article_alias_uz_cy]
               [:article.alias_uz_la :article_alias_uz_la]
               [:article.alias_ru :article_alias_ru]
               [:article.number :article_number]
               [:article.factor :article_factor]
               [:offense_type.name_ru :type_name_ru]
               [:offense_type.name_uz_la :type_name_uz_la]
               [:offense_type.name_uz_cy :type_name_uz_cy]
               [:article.url :article_url]
               [:report.citizen_id :citizen_id])
       (from :offense)
       (left-join :article [:= :article.id :offense.article_id]
                  :response [:= :response.id :offense.response_id]
                  :report [:= :report.id :offense.report_id]
                  :offense_type [:= :offense_type.id :offense.type_id])
       (where-offense filter)
       (apply-pagination pagination)
       (order-by (case (:sort filter)
                   "create_time_asc" [:create_time :asc]
                   "create_time_desc" [:create_time :desc]
                   [:create_time :desc])))))


(defn update-offense
  [id offense]
  (-> (update :offense)
      (returning :*)
      (where [:= :id id])
      (sset offense)))


(defn select-citizen-with-reward
  ([filter]
   (select-citizen-with-reward filter nil))
  ([filter pagination]
   (-> (select :citizen.* [(sql/call :coalesce :%sum.offense.reward_amount 0) :reward_sum])
       (from :citizen)
       (left-join :report [:= :report.citizen_id :citizen.id]
                  :offense [:and
                            [:= :report.id :offense.report_id]
                            [:= :offense.status "paid"]])
       (where-citizen filter)
       (group :citizen.id :citizen.phone :citizen.first_name :citizen.last_name
              :citizen.middle_name :citizen.email :citizen.card :citizen.card_bank_id
              :citizen.area_id :citizen.district_id :citizen.address :citizen.second_phone
              :citizen.zipcode :citizen.create_time :citizen.number :citizen.balance
              :citizen.password :citizen.document :citizen.upload_forbidden :citizen.creator_client_id)
       (assoc :order-by
              (remove nil?
                      [(when (:q filter)
                         [(sql/call :ts_rank :citizen.document
                                    (sql/call :to_tsquery (:q filter))) :desc])
                       [:reward_sum :desc]
                       [:create_time :desc]]))
       (apply-pagination pagination))))

(defn where-area
  [sql-map {:keys [id obsolete]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :id id]))
      (merge-where (when (some? obsolete) [:= :obsolete obsolete]))))


(defn select-area
  ([filter]
   (select-area filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :area)
       (where-area filter)
       (order-by [:number])
       (apply-pagination pagination))))


(defn insert-area
  [& areas]
  (-> (insert-into :area)
      (returning :*)
      (values areas)))


(defn update-area
  [id area]
  (-> (update :area)
      (returning :*)
      (where [:= :id id])
      (sset area)))


(defn where-district
  [sql-map {:keys [id area_id obsolete]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :district.id id]))
      (merge-where (when area_id [:uuid-in :district.area_id area_id]))
      (merge-where (when (some? obsolete) [:= :district.obsolete obsolete]))))


(defn select-district
  ([filter]
   (select-district filter nil))
  ([filter pagination]
   (-> (select :district.*
               [:area.obsolete :area_obsolete]
               [:area.name_uz_cy :area_name_uz_cy]
               [:area.name_uz_la :area_name_uz_la]
               [:area.name_ru :area_name_ru])
       (from :district)
       (left-join :area [:= :area_id :area.id])
       (where-district filter)
       (order-by [:district.number])
       (apply-pagination pagination))))


(defn insert-district
  [& districts]
  (-> (insert-into :district)
      (returning :*)
      (values districts)))


(defn update-district
  [id district]
  (-> (update :district)
      (returning :*)
      (where [:= :id id])
      (sset district)))


(defn where-faq
  [sql-map {:keys [id]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :id id]))))


(defn select-faq
  ([filter]
   (select-faq filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :faq)
       (where-faq filter)
       (order-by [:number])
       (apply-pagination pagination))))


(defn insert-faq
  [& faqs]
  (-> (insert-into :faq)
      (returning :*)
      (values faqs)))


(defn update-faq
  [id faq]
  (-> (update :faq)
      (returning :*)
      (where [:= :id id])
      (sset faq)))


(defn where-article
  [sql-map {:keys [number id obsolete citizen_selection_enabled]}]
  (-> sql-map
      (merge-where (when number [:str-in :number number]))
      (merge-where (when id [:in :id id]))
      (merge-where (when (some? obsolete) [:= :obsolete obsolete]))
      (merge-where (when (some? citizen_selection_enabled) [:= :citizen_selection_enabled citizen_selection_enabled]))))

(defn where-offense-type
  [sql-map {:keys [id]}]
  (-> sql-map
      (merge-where (when id [:in :id id]))))

(defn select-article
  ([filter]
   (select-article filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :article)
       (where-article filter)
       (order-by [:number])
       (apply-pagination pagination))))


(defn select-offense-type
  ([filter]
   (select-offense-type filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :offense_type)
       (where-offense-type filter)
       (order-by [:id])
       (apply-pagination pagination))))


(defn insert-article
  [& articles]
  (-> (insert-into :article)
      (returning :*)
      (values articles)))


(defn insert-offense-type
  [& offense-types]
  (-> (insert-into :offense_type)
      (returning :*)
      (values offense-types)))


(defn update-article
  [id article]
  (-> (update :article)
      (returning :*)
      (where [:= :id id])
      (sset article)))


(defn update-offense-type
  [id offense-type]
  (-> (update :offense_type)
      (returning :*)
      (where [:= :id id])
      (sset offense-type)))


(defn delete-article [filter]
  (-> (delete-from :article)
      (where-article filter)))


(defn where-response
  [sql-map {:keys [id]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :id id]))))


(defn select-response
  ([filter]
   (select-response filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :response)
       (where-response filter)
       (order-by :obsolete :priority)
       (apply-pagination pagination))))


(defn insert-response
  [& responses]
  (-> (insert-into :response)
      (returning :*)
      (values responses)))


(defn update-response
  [id response]
  (-> (update :response)
      (returning :*)
      (where [:= :id id])
      (sset response)))


(defn where-staff
  [sql-map {:keys [id username role area_id district_id name q]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :staff.id id]))
      (merge-where (when username [:str-in :staff.username username]))
      (merge-where (when role [:uuid-in :staff.role role]))
      (merge-where (when area_id [:uuid-in :staff.area_id area_id]))
      (merge-where (when district_id [:uuid-in :staff.district_id district_id]))
      (merge-where (when q [:or
                            (sql/call "@@" :staff.document (util/convert-to-cyr (prepare-q q)))
                            (sql/call "@@" :staff.document (util/convert-to-lat (prepare-q q)))]))
      (merge-where (when name (let [s (str "%" name "%")]
                                [:or
                                 [:ilike :first_name s]
                                 [:ilike :last_name s]
                                 [:ilike :middle_name s]])))))


(defn select-staff
  ([filter]
   (select-staff filter nil))
  ([filter pagination]
   (-> (select :staff.*)
       (from :staff)
       (where-staff filter)
       (order-by [:number])
       (apply-pagination pagination))))


(defn insert-staff
  [& staffs]
  (-> (insert-into :staff)
      (returning :*)
      (values staffs)))


(defn update-staff
  [id staff]
  (-> (update :staff)
      (returning :*)
      (where [:= :id id])
      (sset staff)))


(defn insert-vehicle
  [& vehicles]
  (-> (insert-into :vehicle)
      (returning :*)
      (on-conflict :id)
      (do-nothing)
      (values vehicles)))


(defn insert-offense
  [& offenses]
  (-> (insert-into :offense)
      (returning :*)
      (values offenses)))


(defn where-organization
  [sql-map {:keys [id citizen_id]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :id id]))
      (merge-where (when citizen_id [:uuid-in :citizen_id citizen_id]))))


(defn select-organization
  ([filter]
   (select-organization filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :organization)
       (where-organization filter)
       (apply-pagination pagination))))


(defn insert-organization
  [& organizations]
  (-> (insert-into :organization)
      (returning :*)
      (values organizations)))


(defn update-organization
  [id organization]
  (-> (update :organization)
      (returning :*)
      (where [:= :id id])
      (sset organization)))


(defn where-revision
  [sql-map {:keys [id version report_id]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :id id]))
      (merge-where (when version [:int-in :version version]))
      (merge-where (when report_id [:uuid-in :report_id report_id]))))


(defn select-revision
  ([filter]
   (select-revision filter nil))
  ([filter pagination]
   (-> (select :*)
       (from [{:select [:* [#sql/raw "row_number() over (partition by report_id)" :version]]
               :from   [:revision]}
              :revision])
       (where-revision filter)
       (apply-pagination pagination))))


(defn insert-revision
  [& revisions]
  (-> (insert-into :revision)
      (returning :*)
      (values revisions)))


(defn update-revision
  [id invalidate_time revision]
  (-> (update :revision)
      (returning :report_id :invalidate_time)
      (where [:and
              [:= :report_id id]
              [:= :invalidate_time invalidate_time]])
      (sset revision)))


(defn very-non-reusable-offense-stats-condition
  "Intended to be used only inside offense-stats function"
  [range fine-date-compare-operator]
  [:and [:< :offense.fine_date (:lt range)]
   [fine-date-compare-operator [:diff-days (:lt range) :offense.fine_date] 60]
   [:or [:= :offense.dismiss_time nil]
    [:>= :offense.dismiss_time (:lt range)]]
   [:or [:= :offense.pay_time nil]
    [:>= :offense.pay_time (:lt range)]]])


(defn offense-stats
  [level {:keys [date_range area_id]}]
  (-> (with [:t (-> (from :report)
                    (group :report.id)
                    (left-join :offense [:= :report.id :offense.report_id])
                    (select :report.*
                            [(count-when [:within :offense.create_time date_range]) :count]
                            [(count-when [:and [:< :offense.create_time (:lt date_range)]
                                               [:or [:>= :offense.accept_time (:lt date_range)]
                                                    [:>= :offense.reject_time (:lt date_range)]
                                                    [:and [:= :offense.accept_time nil]
                                                          [:= :offense.reject_time nil]]]]) :pending_count]
                            [(count-when [:within :offense.accept_time date_range]) :accepted_count]
                            [(count-when [:within :offense.reject_time date_range]) :rejected_count]
                            [(sum-when :fine [:within :offense.fine_date date_range]) :fine_sum]
                            [(count-when :fine [:within :offense.fine_date date_range]) :fine_count]
                            [(sum-when :fine [:within :offense.pay_time date_range]) :paid_fine_sum]
                            [(count-when :fine [:within :offense.pay_time date_range]) :paid_fine_count]
                            [(sum-when :fine (very-non-reusable-offense-stats-condition date_range :<=)) :unpaid_fine_sum]
                            [(count-when :fine (very-non-reusable-offense-stats-condition date_range :<=)) :unpaid_fine_count]
                            [(sum-when :fine (very-non-reusable-offense-stats-condition date_range :>)) :expired_fine_sum]
                            [(count-when :fine (very-non-reusable-offense-stats-condition date_range :>)) :expired_fine_count]))])
      (select
        [(count-when :report.id [:within :report.create_time date_range]) :report_count]
        (sql/qualify level :name_ru) (sql/qualify level :name_uz_cy) (sql/qualify level :name_uz_la)
        [:%sum.pending_count :pending_count]
        [:%sum.count :count]
        [:%sum.accepted_count :accepted_count]
        [:%sum.rejected_count :rejected_count]
        [:%sum.fine_sum :fine_sum]
        [:%sum.fine_count :fine_count]
        [:%sum.unpaid_fine_sum :unpaid_fine_sum]
        [:%sum.unpaid_fine_count :unpaid_fine_count]
        [:%sum.expired_fine_sum :expired_fine_sum]
        [:%sum.expired_fine_count :expired_fine_count]
        [:%sum.paid_fine_sum :paid_fine_sum]
        [:%sum.paid_fine_count :paid_fine_count])
      (from level)
      (left-join [:t :report] [:= (sql/qualify level :id) (sql/qualify :report (column level :_id))])
      (group (sql/qualify level :id))
      (order-by (sql/qualify level :number))
      (merge-where (when (and (= level :district) area_id)
                     [:uuid-in :district.area_id area_id]))))


(defn article-funnel
  [level {:keys [date_range area_id today]}]
  (-> (with [:t (-> (from :article)
                    (group :article.id :report.area_id :report.district_id)
                    (left-join :offense [:= :article.id :offense.article_id]
                               :report [:= :offense.report_id :report.id])
                    (where [:within :offense.create_time date_range])
                    (select :article.*
                            [:report.area_id :area_id]
                            [:report.district_id :district_id]
                            [(count-when [:and [:!= nil :offense.forward_time] [:= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time]]) :pending_fined_count]

                            ;; Failed to be sent to ASBT
                            [(count-when [:and [:= nil :offense.forward_time] [:!= nil :offense.failure_time]]) :failed_count]

                            ;; Offense that were dismissed by judge
                            [(count-when [:!= nil :offense.dismiss_time]) :dismissed_count]

                            ;; Offense that have paid fine
                            [(sum-when :fine [:and [:!= nil :offense.fine_date] [:!= nil :offense.pay_time]]) :paid_fine_sum]
                            [(count-when :fine [:and [:!= nil :offense.fine_date] [:!= nil :offense.pay_time]]) :paid_fine_count]

                            [(sum-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:<= [:diff-days today :offense.fine_date] 60]]) :unpaid_fine_sum]
                            [(count-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:<= [:diff-days today :offense.fine_date] 60]]) :unpaid_fine_count]

                            [(sum-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:< 60 [:diff-days today :offense.fine_date]]]) :expired_fine_sum]
                            [(count-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:< 60 [:diff-days today :offense.fine_date]]]) :expired_fine_count]

                            ;; Accepted by inspector
                            [(count-when [:!= nil :offense.accept_time]) :accepted_count]))])

      (select
        :number
        :text_uz_cy
        :text_uz_la
        :text_ru
        :alias_uz_cy
        :alias_uz_la
        :alias_ru
        [:%sum.failed_count :failed_count]
        [:%sum.pending_fined_count :pending_fined_count]
        [:%sum.dismissed_count :dismissed_count]
        [:%sum.paid_fine_sum :paid_fine_sum]
        [:%sum.paid_fine_count :paid_fine_count]
        [:%sum.unpaid_fine_sum :unpaid_fine_sum]
        [:%sum.unpaid_fine_count :unpaid_fine_count]
        [:%sum.expired_fine_sum :expired_fine_sum]
        [:%sum.expired_fine_count :expired_fine_count]
        [:%sum.accepted_count :accepted_count])

      (from [:t :article])
      (group :text_uz_cy :text_uz_la :text_ru :alias_uz_cy :alias_uz_la :alias_ru :number)
      (order-by [:accepted_count :desc])
      (merge-where (cond
                     (and (= level :district) area_id)
                     [:uuid-in :district_id (when area_id [area_id])]

                     (and (= level :area) area_id)
                     [:uuid-in :area_id (when area_id [area_id])]))))


(defn offenses-funnel
  [level {:keys [date_range area_id today]}]
  (-> (with [:t (-> (from :report)
                    (group :report.id)
                    (left-join :offense [:= :report.id :offense.report_id])
                    (where [:within :offense.create_time date_range])
                    (select :report.*
                            [:%count.* :count]

                            [(count-when [:and [:= :offense.accept_time nil] [:= :offense.reject_time nil]]) :pending_count]

                            [(count-when [:!= nil :offense.accept_time]) :accepted_count]

                            [(count-when [:!= nil :offense.reject_time]) :rejected_count]

                            [(count-when [:and [:= nil :offense.forward_time] [:!= nil :offense.failure_time]]) :failed_count]

                            [(count-when [:!= nil :offense.forward_time]) :forwarded_count]

                            [(count-when [:and [:!= nil :offense.forward_time] [:= nil :offense.fine_date] [:= nil :offense.dismiss_time]]) :pending_fined_count]

                            [(count-when [:!= nil :offense.dismiss_time]) :dismissed_count]

                            [(count-when [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time]]) :fined_count]

                            [(sum-when :fine [:and [:!= nil :offense.fine_date] [:!= nil :offense.pay_time]]) :paid_fine_sum]

                            [(count-when :fine [:and [:!= nil :offense.fine_date] [:!= nil :offense.pay_time]]) :paid_fine_count]

                            [(sum-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:<= [:diff-days today :offense.fine_date] 60]]) :unpaid_fine_sum]

                            [(count-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:<= [:diff-days today :offense.fine_date] 60]]) :unpaid_fine_count]

                            [(sum-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:< 60 [:diff-days today :offense.fine_date]]]) :expired_fine_sum]

                            [(count-when :fine [:and [:!= nil :offense.fine_date] [:= nil :offense.dismiss_time] [:= nil :offense.pay_time] [:< 60 [:diff-days today :offense.fine_date]]]) :expired_fine_count]))])
      (select
        (sql/qualify level :name_ru) (sql/qualify level :name_uz_cy) (sql/qualify level :name_uz_la)
        [:%sum.count :count]
        [:%sum.pending_count :pending_count]
        [:%sum.accepted_count :accepted_count]
        [:%sum.rejected_count :rejected_count]
        [:%sum.failed_count :failed_count]
        [:%sum.forwarded_count :forwarded_count]
        [:%sum.pending_fined_count :pending_fined_count]
        [:%sum.dismissed_count :dismissed_count]
        [:%sum.fined_count :fined_count]
        [:%sum.paid_fine_sum :paid_fine_sum]
        [:%sum.paid_fine_count :paid_fine_count]
        [:%sum.unpaid_fine_sum :unpaid_fine_sum]
        [:%sum.unpaid_fine_count :unpaid_fine_count]
        [:%sum.expired_fine_sum :expired_fine_sum]
        [:%sum.expired_fine_count :expired_fine_count])
      (from level)
      (left-join [:t :report] [:= (sql/qualify level :id) (sql/qualify :report (column level :_id))])
      (group (sql/qualify level :id))
      (order-by (sql/qualify level :number))
      (merge-where (when (and (= level :district) area_id)
                     [:uuid-in :district.area_id area_id]))))


(defn fines-rewards-stats [{:keys [date_range area_id district_id reward_type]}]
  #_(doall
      (let [{:keys [gte lt]} date_range]
        (when (and gte (.isBefore gte (.minusMonths (util/today) 3)))
          (throw (Exception. "Date range should be less than 3 months")))
        (when (and lt (.isBefore lt (.minusMonths (util/today) 3)))
          (throw (Exception. "Date range should be less than 3 months")))))
  (->
    (select
      [:offense.fine :fine]
      [:%count.offense.fine :fine_count]
      [(sql/call :* :%count.offense.fine :offense.fine) :fine_sum]
      [:reward.amount :reward]
      [:%count.reward.id :reward_count]
      [(sql/call :* :%count.reward.id :reward.amount) :reward_sum])
    (from :offense)
    (merge-join :report [:= :report.id :offense.report_id])
    (merge-join :reward [:= :reward.id :offense.reward_id])
    (merge-where (when date_range [:within :reward.pay_time date_range]))
    (merge-where (when area_id [:uuid-in :report.area_id area_id]))
    (merge-where (when district_id [:uuid-in :report.district_id district_id]))
    (merge-where (when (and reward_type
                            (not= reward_type "inspector"))
                   [:and
                    [:= :reward.staff_id nil]
                    [:= :reward.type reward_type]]))
    (merge-where (when (and reward_type (= reward_type "inspector"))
                   [:!= :reward.staff_id nil]))
    (order-by :offense.fine :reward.amount)
    (group :offense.fine :reward.amount)))


(defn expired-reports-stats [ages level filter]
  (-> {:select
       (->> (partition-all 2 1 ages)
            (map
              (fn [[age next-age]]
                [(count-when [:within [:diff-days [:cast (util/today) :date] :report.create_time] {:gte age :lt next-age}])
                 (column "age_" age)]))
            (concat
              (map (partial sql/qualify level)
                   [:id :name_ru :name_uz_cy :name_uz_la])))}
      (group :1)
      (from :report)
      (where-report (merge filter {:status ["created"]}))
      (right-join level [:= (column level :.id) (column level :_id)])))


(defn reviewed-reports-stats [review_times filter]
  (-> {:select
       (->> review_times
            (map-indexed
              (fn [index time]
                [(count-when [:>= :review_time time])
                 (column "count_" (inc index))]))
            (concat
              [:staff.id :area.name_ru :area.name_uz_cy :area.name_uz_la :staff.area_id
               :staff.first_name :staff.middle_name :staff.last_name
               [(count-when [:!= :review_time nil]) :total]]))}
      (from :staff)
      (group :area.id :staff.id)
      (where-staff (assoc filter :role ["inspector"]))
      (merge-left-join :area [:= :staff.area_id :area.id])
      (merge-left-join :report [:= :report.inspector_id :staff.id])
      (order-by :staff.first_name, :staff.middle_name, :staff.last_name)))


(defn where-reward
  [sql-map {:keys [id receiver_role staff_id area_id district_id number create_time status type failure_message transfer_id citizen_id]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :reward.id id]))
      (merge-where (when citizen_id [:uuid-in :report.citizen_id citizen_id]))
      (merge-where (when staff_id [:uuid-in :reward.staff_id staff_id]))
      (merge-where (when number [:int-in :reward.number number]))
      (merge-where (when area_id [:uuid-in :report.area_id area_id]))
      (merge-where (when failure_message [:str-in :reward.failure_message failure_message]))
      (merge-where (when create_time [:within :reward.create_time create_time]))
      (merge-where (when status [:str-in :reward.status status]))
      (merge-where (when transfer_id [:in :reward.transfer_id transfer_id]))
      (merge-where (when type [:str-in :reward.type type]))
      (merge-where (when (= receiver_role "inspector") [:!= :reward.staff_id nil]))
      (merge-where (when (= receiver_role "citizen") [:= :reward.staff_id nil]))
      (merge-where (when district_id [:uuid-in :report.district_id district_id]))))


(defn select-reward
  ([filter]
   (select-reward filter nil))
  ([filter pagination]
   (-> (select :reward.*
               :citizen.first_name
               :citizen.last_name
               :citizen.middle_name
               :citizen.phone
               [:staff.first_name :staff_first_name]
               [:staff.last_name :staff_last_name]
               [:staff.middle_name :staff_middle_name]
               [:staff.phone :staff_phone]
               [:staff.rank :staff_rank]
               :report.area_id
               :report.district_id
               [:report.id :report_id]
               [:citizen.id :citizen_id])
       (from :reward)
       (left-join :offense [:= :offense.reward_id :reward.id]
                  :report [:= :report.id :offense.report_id]
                  :citizen [:= :citizen.id :report.citizen_id]
                  :staff [:= :staff.id :reward.staff_id])
       (where-reward filter)
       (apply-pagination pagination)
       (order-by [:reward.create_time :desc]))))


(defn select-offense-reward
  ([report-id]
   (-> (select :reward.*)
       (from :report)
       (join :offense [:= :report.id :offense.report_id]
             :reward [:= :offense.reward_id :reward.id])
       (where-report {:id [report-id]})
       (group :reward.id)
       (order-by [:reward.create_time :desc]))))


(defn insert-reward
  [& rewards]
  (-> (insert-into :reward)
      (returning :*)
      (values (map #(update-existing % :params db/raw-value) rewards))
      (on-conflict :number)
      (do-update-set :number)))


(defn update-reward
  [id reward]
  (-> (update :reward)
      (returning :*)
      (where [:= :id id])
      (sset (update-existing reward :params db/raw-value))))


(defn where-oauth-token
  [sql-map {:keys [id citizen_id client_id user_id create_time access_expire_time refresh_expire_time scope access_token refresh_token revoked]}]
  (-> sql-map
      (merge-where (when (seq id) [:uuid-in :oauth_token.id id]))
      (merge-where (when (some? revoked) [:= :oauth_token.revoked revoked]))
      (merge-where (when (seq scope) [:contains? :oauth_token.scope scope]))
      (merge-where (when (seq client_id) [:uuid-in :oauth_token.client_id client_id]))
      (merge-where (when (seq citizen_id) [:uuid-in :oauth_token.citizen_id citizen_id]))
      (merge-where (when (seq user_id) [:or [:uuid-in :oauth_token.citizen_id user_id]
                                        [:uuid-in :oauth_token.staff_id user_id]]))
      (merge-where (when access_token [:= :oauth_token.access_token access_token]))
      (merge-where (when refresh_token [:= :oauth_token.refresh_token refresh_token]))
      (merge-where (when create_time [:within :oauth_token.create_time create_time]))
      (merge-where (when access_expire_time [:within :oauth_token.access_expire_time access_expire_time]))
      (merge-where (when refresh_expire_time [:within :oauth_token.refresh_expire_time refresh_expire_time]))))


(defn where-oauth-client
  [sql-map {:keys [id secret allowed_scope default_scope redirect_uri enabled]}]
  (-> sql-map
      (merge-where (when (seq id) [:uuid-in :oauth_client.id id]))
      (merge-where (when (some? enabled) [:= :oauth_client.enabled enabled]))
      (merge-where (when allowed_scope [:contains? :oauth_client.allowed_scope allowed_scope]))
      (merge-where (when default_scope [:contains? :oauth_client.default_scope default_scope]))
      (merge-where (when secret [:= :oauth_client.secret secret]))
      (merge-where (when (seq redirect_uri) [:contains? :oauth_client.redirect_uri redirect_uri]))))


(defn select-oauth-client
  ([filter]
   (select-oauth-client filter nil))
  ([filter pagination]
   (-> (select
         [:id :client_id]
         [:secret :client_secret]
         [:enabled :client_enabled]
         :*)
       (from :oauth_client)
       (where-oauth-client filter)
       (order-by [:enabled :desc] [:create_time :desc])
       (apply-pagination pagination))))


(defn select-oauth-token
  ([filter]
   (select-oauth-token filter nil))
  ([filter pagination]
   (-> (select
         :oauth_token.*
         [:oauth_client.id :client_id]
         [:oauth_client.url :client_url]
         [:oauth_client.name :client_name]
         [:oauth_client.logo :client_logo]
         [:oauth_client.allowed_scope :client_allowed_scope]
         [:oauth_client.default_scope :client_default_scope]
         [:oauth_client.secret :client_secret]
         [:oauth_client.enabled :client_enabled]
         [:oauth_client.redirect_uri :client_redirect_uri])
       (from :oauth_token)
       (join :oauth_client [:= :oauth_client.id :oauth_token.client_id])
       (where-oauth-token filter)
       (where-oauth-client (select-keys filter [:redirect_uri :enabled :allowed_scope]))
       (order-by [:oauth_token.create_time :desc])
       (apply-pagination pagination))))


(defn insert-oauth-client
  [& clients]
  (-> (insert-into :oauth_client)
      (returning :*)
      (values (map
                (fn [client]
                  (-> client
                      (update-existing :default_scope #(some->> (not-empty %)
                                                                (sql/call :to-array)))
                      (update-existing :allowed_scope #(some->> (not-empty %)
                                                                (sql/call :to-array)))
                      (update-existing :redirect_uri #(some->> (not-empty %)
                                                               (sql/call :to-array)))))
                clients))))


(defn update-oauth-client
  [id client]
  (-> (update :oauth_client)
      (returning :*)
      (where-oauth-client {:id id})
      (sset (-> client
                (update-existing :default_scope #(some->> (not-empty %)
                                                          (sql/call :to-array)))
                (update-existing :allowed_scope #(some->> (not-empty %)
                                                          (sql/call :to-array)))
                (update-existing :redirect_uri #(some->> (not-empty %)
                                                         (sql/call :to-array)))))))


(defn insert-oauth-token
  [& tokens]
  (-> (insert-into :oauth_token)
      (returning :*)
      (values
        (map (fn [oauth] (update-existing oauth :scope #(sql/call :to-array %)))
             tokens))))


(defn update-oauth-token
  [filter token]
  (-> (update :oauth_token)
      (returning :*)
      (where-oauth-token filter)
      (sset (update-existing token :scope #(sql/call :to-array %)))))


(defn select-oauth [filter]
  (->
    (select
      [:oauth_token.id :token_id]
      [:oauth_token.citizen_id :token_citizen_id]
      [:oauth_token.staff_id :token_staff_id]
      [:oauth_token.client_id :token_client_id]
      [:oauth_token.scope :token_scope]
      [:oauth_token.access_token :token_access_token]
      [:oauth_token.refresh_token :token_refresh_token]
      [:oauth_token.create_time :token_create_time]
      [:oauth_token.refresh_time :token_refresh_time]
      [:oauth_token.revoked :token_revoked]
      [:oauth_token.access_expire_time :token_access_expire_time]
      [:oauth_token.refresh_expire_time :token_refresh_expire_time]

      [:oauth_client.id :client_id]
      [:oauth_client.secret :client_secret]
      [:oauth_client.name :client_name]
      [:oauth_client.allowed_scope :client_allowed_scope]
      [:oauth_client.default_scope :client_default_scope]
      [:oauth_client.redirect_uri :client_redirect_uri]
      [:oauth_client.url :client_url]
      [:oauth_client.grant_type :client_grant_type]
      [:oauth_client.error_redirect_uri :client_error_redirect_uri]
      [:oauth_client.logo :client_logo]
      [:oauth_client.enabled :client_enabled]
      [:oauth_client.create_time :client_create_time])
    (from :oauth_token)
    (join :oauth_client [:= :oauth_token.client_id :oauth_client.id])
    (where-oauth-token (select-keys filter [:access_token :revoked :access_expire_time]))
    (where-oauth-client (select-keys filter [:enabled]))))


(defn where-transfer
  [sql-map {:keys [id create_time bank_account status number]}]
  (-> sql-map
      (merge-where (when id [:uuid-in :transfer.id id]))
      (merge-where (when status [:str-in :transfer.status status]))
      (merge-where (when number [:int-in :transfer.number number]))
      (merge-where (when create_time [:within :transfer.create_time create_time]))
      (merge-where (when bank_account (let [bank_account (str "%" bank_account "%")]
                                        [:ilike :transfer.bank_account bank_account])))))


(defn select-transfer
  ([filter]
   (select-transfer filter nil))
  ([filter pagination]
   (-> (select :*)
       (from :transfer)
       (where-transfer filter)
       (order-by [:number :desc])
       (apply-pagination pagination))))


(defn insert-transfer
  [& transfers]
  (-> (insert-into :transfer)
      (returning :*)
      (values transfers)))


(defn update-transfer
  [id transfer]
  (-> (update :transfer)
      (returning :*)
      (where [:= :id id])
      (sset transfer)))
