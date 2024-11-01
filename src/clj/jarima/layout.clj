(ns jarima.layout
  (:require
    [hiccup.core :as h]
    [clojure.edn :as edn]
    [jarima.util :as util]
    [jarima.spec :as spec]
    [markdown.core :as md]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [cheshire.core :as json]
    [clojure.string :as str]
    [selmer.parser :as parser]
    [selmer.filters :as filters]
    [clojure.tools.logging :as log]
    [selmer.tags :refer [expr-tags]]
    [medley.core :refer [assoc-some]]
    [selmer.util :refer [parse-accessor]]
    [ring.util.codec :refer [form-encode]]
    [jarima.config :refer [env dictionary]]
    [medley.core :refer [map-vals map-keys]]
    [markdown.core :refer [md-to-html-string]]
    [ring.util.anti-forgery :refer [anti-forgery-field]]
    [selmer.filter-parser :refer [lookup-args escape-html]]
    [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
    [ring.util.http-response :as response :refer [content-type ok bad-request]]
    [clojure.set :as set]))

;; default value is here
(def ^:dynamic *request* nil)


(def last-request nil)


(defonce mission-translations (atom {:ru #{} :uz_cy #{} :uz_la #{}}))


(parser/set-resource-path! (clojure.java.io/resource "html"))


(def translator
  (memoize
    (fn [available-locales]
      (apply some-fn
             (map (fn [locale]
                    (if (= :ru locale)
                      identity
                      #(get-in dictionary [:translations locale %])))
                  available-locales)))))


(defn t [text & args]
  (when (some? text)
    (let [locale (or (:locale *request*)
                     (get-in *request* [:query-params "locale"])
                     :ru)]
      (when-not (or (= :ru locale)
                    (get-in dictionary [:translations locale text]))
        (log/warn "Translation is missing" (:locale *request*) text)
        (when-not (:prod env)
          (swap! mission-translations update (:locale *request*) #(conj % text))))
      (let [f (translator
                (keys (util/available-locales (:translations dictionary) locale)))]
        (apply format (f text) args)))))


(def localized-getter
  (memoize
    (fn [base available-locales]
      (apply some-fn
             (->> available-locales
                  (map #(->> (name %)
                             (str (name base) "_")
                             (keyword))))))))


(defn t-get [m k]
  (when m
    (let [t-key (localized-getter
                  (keyword k)
                  (keys (util/available-locales
                          (:translations dictionary)
                          (:locale *request*))))]
      (t-key m))))


(filters/add-filter! :t t)


(filters/add-filter! :t-get t-get)


(filters/add-filter! :name
  (fn [x] (when x (name x))))


(parser/add-tag! :csrf-field
  (fn [_ _] (anti-forgery-field)))


(filters/add-filter! :markdown
  (fn [content] [:safe (md-to-html-string content)]))


(filters/add-filter! :pos? pos?)


(filters/add-filter! :phone
  (fn [phone]
    [:safe
     (h/html [:a {:href (str "tel:" phone)}
              (util/format-phone  phone)])]))


(filters/add-filter! :local-date-time-precise util/unparse-local-date-time-precise)


(filters/add-filter! :local-date-time util/unparse-local-date-time)


(filters/add-filter! :local-date util/unparse-local-date)


(filters/add-filter! :local-date-short util/unparse-local-date-short)


(filters/add-filter! :local-date-long util/unparse-local-date-long)


(filters/add-filter! :money #(format "%,2.0f" (double (or % 0))))


(filters/add-filter! :get get)


(defn bank-name [bank-id]
  (get-in dictionary [:banks bank-id]))


(filters/add-filter! :bank-name bank-name)


(defn area-name [id]
  (when-let [area (-> {:select [:*]
                       :from   [:area]
                       :where  [:= :id id]}
                      (db/query)
                      (first))]
    (t-get area :name)))

(filters/add-filter! :area-name area-name)


(defn oauth-client [id]
  (-> {:select [:*]
       :from   [:oauth_client]
       :where  [:= :id id]}
      (db/query)
      (first)))


(filters/add-filter! :oauth-client oauth-client)


(defn district-name [id]
  (when-let [district (-> {:select [:*]
                           :from   [:district]
                           :where  [:= :id id]}
                          (db/query)
                          (first))]
    (t-get district :name)))


(filters/add-filter! :district-name district-name)


(defn address
  ([addressable]
   (address addressable true))
  ([addressable full?]
   (address addressable full? false))
  ([{:keys [area_id district_id address]} full? multiline?]
   (let [full? (if (string? full?) (edn/read-string full?) full?)
         multiline? (if (string? multiline?) (edn/read-string multiline?) multiline?)]
     (not-empty
       (str (->> [(area-name area_id)
                  (district-name district_id)]
                 (filter identity)
                 (str/join ", "))
            (when (and full? address)
              (str (if multiline? "\n" ", ") address)))))))


(filters/add-filter! :address address)


(filters/add-filter! :includes?
  (fn [xs x]
    (boolean (some #{x} xs))))


(filters/add-filter! :map
  (fn [xs kw]
    (let [kw (keyword kw)]
      (map kw xs))))


(defn render
  [template & [params response]]
  (let [response (or response ok)]
    (-> template
        (parser/render-file (merge {:csrf-token *anti-forgery-token* :request *request* :env env} params))
        (response)
        (content-type "text/html; charset=utf-8"))))


(defn error-page
  [error-details]
  (-> "metronic/error.html"
      (parser/render-file (assoc error-details :request *request*))
      (bad-request)
      (content-type "text/html; charset=utf-8")
      (assoc-some :status (:status error-details))))


(defn error-page!
  [error-details]
  (response/throw!
    (error-page error-details)))


(defn area-select-html
  [name required? value include-obsolete? disabled?]
  (h/html
    [:select.form-control {:name name :required required? :disabled disabled?}
     [:option {:value ""} ""]
     (->> (let [staff (:identity *request*)]
            (cond-> {}
              (and (= "inspector" (:role staff)) (:area_id staff))
              (assoc :id [(:area_id staff)])
              (not include-obsolete?)
              (assoc :obsolete false)))
          (q/select-area)
          (db/query)
          (map (fn [area]
                 [:option {:value      (:id area)
                           :data-yname (t-get area :yname)
                           :selected   (= (:id area) value)}
                  (str (t-get area :name) " "
                       (when (:obsolete area)
                         (format "(%s)" (t "устарел"))))])))]))


(parser/add-tag! :area-select
  (fn [[name required value include-obsolete? disabled?] context]
    (let [lookup (lookup-args context)]
      (area-select-html
        (lookup name)
        (boolean (edn/read-string (lookup required)))
        (lookup value)
        (boolean (edn/read-string (lookup include-obsolete?)))
        (boolean (edn/read-string (lookup disabled?)))))))


(defn district-select-html
  [name required value include-obsolete?]
  (h/html
    [:select.form-control {:name name :required required}
     [:option {:value ""} ""]
     (->> (let [staff (:identity *request*)]
            (cond-> {}
              (and (= "inspector" (:role staff))
                   (:area_id staff))
              (assoc :area_id [(:area_id staff)])
              (and (= "inspector" (:role staff))
                   (:district_id staff))
              (assoc :id [(:district_id staff)])
              (not include-obsolete?)
              (assoc :obsolete false)))
          (q/select-district)
          (db/query)
          (map (fn [district]
                 [:option {:value        (:id district)
                           :data-area-id (:area_id district)
                           :data-yname   (t-get district :yname)
                           :selected     (= (:id district) value)}
                  (str (t-get district :name) " "
                       (when (:obsolete district)
                         (format "(%s)" (t "устарел"))))])))]))



(parser/add-tag! :district-select
  (fn [[name required value include-obsolete?] context]
    (let [lookup (lookup-args context)]
      (district-select-html
        (lookup name)
        (boolean (edn/read-string (lookup required)))
        (lookup value)
        (boolean (edn/read-string (lookup include-obsolete?)))))))


(parser/add-tag! :offense-taken-measures
  (fn [[offense] context]
    (let [lookup (lookup-args context)
          offense (lookup offense)
          status (:status offense)]
      (cond
        (= status "created")
        nil

        (= status "rejected")
        (t "Отклонен по причине: %s"
           (str (t-get offense :response_text) " "
                (:extra_response offense)))

        :else
        (t "Наказание по статье %s: %s"
           (str (:article_number offense)
                (when (:article_factor offense)
                  (format " (%s %s)" (:article_factor offense) (t "МРЗП"))))
           (t-get offense :article_text))))))


(defn reward [params]
  (cond
    (:phone params) (t "Пополнение мобильного счета: %s" (util/format-phone (:phone params)))
    (:card params) (t "Перевод на карту: %s" (if (string? (:card params))
                                               (util/format-card (:card params))
                                               (t "На карту указанную в профиле")))
    (:fund params) (t "Пожертвование в фонд: %s" (t (:fund params)))
    (:bank params) (t "Банковский перевод: %s" (:bank params))
    (:no-reward params) (t "Без вознаграждения")
    :else (first (vals params))))


(defn reward-icon [params]
  (:icon (cond
           (:phone params) (get spec/reward-types "phone")
           (:card params) (get spec/reward-types "card")
           (:fund params) (get spec/reward-types "fund")
           (:bank params) (get spec/reward-types "bank")
           (:no-reward params) (get spec/reward-types "no-reward")
           :else "")))


(filters/add-filter! :reward reward)


(filters/add-filter! :reward-icon reward-icon)


(defn pagination [{:keys [current pages query-string]}]
  (when (< 1 (count pages))
    (h/html
      [:nav.pagination.is-centered.is-rounded
       [:ul.pagination-list
        (for [page pages]
          (if (nil? page)
            [:li [:span.pagination-ellipsis "…"]]
            [:li [:a {:href  (str "?" query-string "&page=" page)
                      :class (str "pagination-link"
                                  (when (= current page)
                                    " is-current"))} page]]))]])))


(defn lookup-args*
  [context-map]
  (fn [^String arg]
    (if (and (> (count arg) 1) (.startsWith arg "@"))
      (let [accessor (parse-accessor (subs arg 1))]
        (get-in context-map accessor))
      arg)))


(parser/add-tag! :asbt-reward
  (fn [[params] context]
    (let [lookup (lookup-args* context)
          params (lookup params)
          reward-type (ffirst params)
          fund (get-in env [:payment :fund (:fund params)])]
      (when (#{:no-reward :fund} reward-type)
        (h/html
          [:div
           [:h4
            (case reward-type
              :fund (t "Вознаграждение пойдёт в фонд")
              :no-reward (t "Заявитель отказался от вознаграждения"))]
           [:div
            [:a
             {:href   (:url fund)
              :target "_blank"}
             (t (:fund params))]]])))))



(parser/add-tag! :json-script
  (fn [[id data] context]
    (let [lookup (lookup-args* context)]
      (h/html
        [:script {:type "application/json" :id (lookup id)}
         (json/encode (lookup data))]))))


(defn path [[name & args] context]
  (let [lookup (lookup-args* context)
        [path-params [_ & query-params]] (split-with #(not= % "?") args)]
    (util/route-path
      (:request context)
      (keyword (lookup name))
      (->> path-params
           (apply hash-map)
           (map-vals lookup)
           (map-keys keyword))
      (->> query-params
           (apply hash-map)
           (map-vals lookup)
           (map-keys keyword)))))


(parser/add-tag! :path path)


(parser/add-tag! :path-abs (comp #(str (:base-url env) %) path))


(parser/add-tag! :menu-item
  (fn [[name path-params query-params] context content]
    (let [lookup (lookup-args* context)]
      (h/html
        [:li
         {:class (str "kt-menu__item "
                      (when (util/route-matched? (:request context) (keyword (lookup name)))
                        "kt-menu__item--here"))}
         [:a.kt-menu__link
          {:href (util/route-path
                   (:request context)
                   (keyword (lookup name))
                   (lookup path-params)
                   (lookup query-params))}
          (-> content :menu-item :content)]])))
  :end-menu-item)


(parser/add-tag! :dropdown-menu-item
  (fn [_ _ content]
    (let [items (-> content :dropdown-menu-sub-items :content)
          title (-> content :dropdown-menu-item :content)]
      (h/html
        [:li
         {:data-ktmenu-submenu-toggle "click"
          :class                      (str "kt-menu__item kt-menu__item--submenu kt-menu__item--rel "
                                           (when (str/index-of items "kt-menu__item--here")
                                             "kt-menu__item--here"))}
         [:a.kt-menu__link.kt-menu__toggle {:href "javascript:;"} title]
         [:div.kt-menu__submenu.kt-menu__submenu--classic.kt-menu__submenu--left
          [:ul.kt-menu__subnav items]]])))
  :dropdown-menu-sub-items :end-dropdown-menu-item)


(defn pager [total current query]
  (when (pos? total)
    (let [left (range 1 current)
          right (range (inc current) (inc total))
          left-padding (+ 4 (max 0 (- 4 (count right))))
          right-padding (+ 4 (max 0 (- 4 (count left))))
          left (if (> (count left) left-padding) (concat [1 nil] (take-last (- left-padding 2) left)) left)
          right (if (> (count right) right-padding) (concat (take (- right-padding 2) right) [nil total]) right)]
      (h/html
        [:div.kt-datatable.kt-datatable--default.m-0.mt-4
         [:div.kt-datatable__pager
          [:ul.kt-datatable__pager-nav
           (concat
             [[:li
               (if (< 1 current)
                 [:a.kt-datatable__pager-link.kt-datatable__pager-link--prev
                  {:href (str "?" query "&page=" (dec current))}
                  [:i.flaticon2-back]]
                 [:a.kt-datatable__pager-link.kt-datatable__pager-link--prev.kt-datatable__pager-link--disabled {}
                  [:i.flaticon2-back]])]]
             (for [i left]
               [:li (if i [:a.kt-datatable__pager-link.kt-datatable__pager-link-number
                           {:href (str "?" query "&page=" i)} i]
                          [:a.kt-datatable__pager-link.kt-datatable__pager-link-number {} "..."])])
             [[:li
               [:a.kt-datatable__pager-link.kt-datatable__pager-link-number.kt-datatable__pager-link--active
                {:href (str "?" query "&page=" current)}
                current]]]
             (for [i right]
               [:li
                (if i [:a.kt-datatable__pager-link.kt-datatable__pager-link-number
                       {:href (str "?" query "&page=" i)} i]
                      [:a.kt-datatable__pager-link.kt-datatable__pager-link-number.kt-datatable__pager-link--disabled {} "..."])])
             [[:li
               (if (> total current)
                 [:a.kt-datatable__pager-link.kt-datatable__pager-link--next
                  {:href (str "?" query "&page=" (inc current))}
                  [:i.flaticon2-next]]
                 [:a.kt-datatable__pager-link.kt-datatable__pager-link--next.kt-datatable__pager-link--disabled {}
                  [:i.flaticon2-next]])]])]]]))))


(parser/add-tag! :pager
  (fn [[total current query-params] context]
    (let [lookup (lookup-args* context)]
      (pager
        (-> total lookup)
        (-> current lookup (or 1))
        (-> query-params lookup (dissoc "page") util/to-query-string)))))


(parser/add-tag! :query-string
  (fn [params context]
    (let [lookup (lookup-args* context)]
      (util/to-query-string
        (reduce
          (fn [acc [k v]]
            (if (= "nil" v)
              (dissoc acc k)
              (assoc acc k (lookup v))))
          (:query-params *request* {})
          (apply hash-map params))))))




(parser/add-tag! :empty-wrapper
  (fn [[xs] context content]
    (if (-> xs keyword context seq)
      (-> content :empty-wrapper :content)
      (or (-> content :empty :content)
          (h/html
            [:div.d-flex.flex-column.align-items-center.p-5
             [:img {:src "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjQiIGhlaWdodD0iNDEiIHZpZXdCb3g9IjAgMCA2NCA0MSIgIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPGcgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoMCAxKSIgZmlsbD0ibm9uZSIgZmlsbC1ydWxlPSJldmVub2RkIj4KICAgIDxlbGxpcHNlIGZpbGw9IiNGNUY1RjUiIGN4PSIzMiIgY3k9IjMzIiByeD0iMzIiIHJ5PSI3Ii8+CiAgICA8ZyBmaWxsLXJ1bGU9Im5vbnplcm8iIHN0cm9rZT0iI0Q5RDlEOSI+CiAgICAgIDxwYXRoIGQ9Ik01NSAxMi43Nkw0NC44NTQgMS4yNThDNDQuMzY3LjQ3NCA0My42NTYgMCA0Mi45MDcgMEgyMS4wOTNjLS43NDkgMC0xLjQ2LjQ3NC0xLjk0NyAxLjI1N0w5IDEyLjc2MVYyMmg0NnYtOS4yNHoiLz4KICAgICAgPHBhdGggZD0iTTQxLjYxMyAxNS45MzFjMC0xLjYwNS45OTQtMi45MyAyLjIyNy0yLjkzMUg1NXYxOC4xMzdDNTUgMzMuMjYgNTMuNjggMzUgNTIuMDUgMzVoLTQwLjFDMTAuMzIgMzUgOSAzMy4yNTkgOSAzMS4xMzdWMTNoMTEuMTZjMS4yMzMgMCAyLjIyNyAxLjMyMyAyLjIyNyAyLjkyOHYuMDIyYzAgMS42MDUgMS4wMDUgMi45MDEgMi4yMzcgMi45MDFoMTQuNzUyYzEuMjMyIDAgMi4yMzctMS4zMDggMi4yMzctMi45MTN2LS4wMDd6IiBmaWxsPSIjRkFGQUZBIi8+CiAgICA8L2c+CiAgPC9nPgo8L3N2Zz4K"}]
             [:span.kt-label-font-color-1.mt-2 (t "Нет данных")]]))))
  :empty :end-empty-wrapper)


(parser/add-tag! :private
  (fn [_ _ content]
    (if (= "admin" (-> *request* :identity :role))
      (-> content :private :content)
      (str "<span class=\"kt-label-font-color-1\">" (t "Скрыто") "</span>")))
  :end-private)


(filters/add-filter! :t t)


(filters/add-filter! :guid util/uuid->guid)


(filters/add-filter! :escape escape-html)


(filters/add-filter! :join-errors #(->> (map t %)
                                        (str/join ", ")))


(filters/add-filter! :md->html (fn [md] [:safe (md/md-to-html-string md)]))


(parser/add-tag! :language-bar
  (fn [_ _]
    (h/html
      [:div.kt-header__topbar-item.kt-header__topbar-item--langs
       [:div.kt-header__topbar-wrapper {:data-toggle "dropdown"}
        [:span.kt-header__topbar-icon
         [:img {:src (->> *request* :locale name (format "/img/%s.svg")) :alt ""}]]]
       [:div.dropdown-menu.dropdown-menu-fit.dropdown-menu-right.dropdown-menu-anim
        [:ul.kt-nav.kt-margin-t-10.kt-margin-b-10
         (for [locale (util/available-locales (:translations dictionary))]
           (let [link (->> locale key name
                           (assoc (:query-params *request*) "locale")
                           util/to-query-string)]
             [:li.kt-nav__item {:class (when (= locale key (-> *request* :locale)) "kt-nav__item--active")}
              [:a.kt-nav__link {:href (str "?" link)}
               [:span.kt-nav__link-icon [:img {:src (format "/img/%s.svg" (-> locale key name))}]]
               [:span.kt-nav__link-text (-> locale val)]]]))]]])))


(filters/add-filter! :offense-status-name
  (fn [status]
    (-> status spec/offense-statuses :name)))


(filters/add-filter! :report-encoder-status-name
  (fn [status]
    (-> status spec/encoder-statuses :name)))


(filters/add-filter! :offense-status-color
  (fn [status]
    (-> status spec/offense-statuses :color)))


(filters/add-filter! :reward-status-name
  (fn [status]
    (-> status spec/reward-statuses :name)))


(filters/add-filter! :reward-status-color
  (fn [status]
    (-> status spec/reward-statuses :color)))


(filters/add-filter! :transfer-status-name
  (fn [status]
    (-> status spec/transfer-statuses :name)))


(filters/add-filter! :transfer-status-color
  (fn [status]
    (-> status spec/transfer-statuses :color)))


(filters/add-filter! :report-status-name
  (fn [status]
    (-> status spec/report-statuses :name)))


(filters/add-filter! :report-status-color
  (fn [status]
    (-> status spec/report-statuses :color)))


(filters/add-filter! :citizen-name
                     (fn [named]
                       (str/join " " (filter seq ((juxt :first_name :middle_name :last_name) named)))))


(filters/add-filter! :staff-name
                     (fn [named]
                       (str/join " " (filter seq ((juxt :staff_first_name :staff_middle_name :staff_last_name) named)))))


(filters/add-filter! :pretty-json
  (fn [x]
    (json/encode x {:pretty true})))


(selmer.filters/add-filter! :open-right-date-range
  (fn [x]
    (when (some? x)
      (util/unparse-open-right-date-range x))))


(selmer.filters/add-filter! :match-scope
  (fn [scopes role]
    (let [oauth-scopes (if (= role "admin")
                         (->> (vals spec/oauth-scopes) (reduce merge {}))
                         (get spec/oauth-scopes role))]
      (->> oauth-scopes
           (keys)
           (set)
           (set/intersection (set scopes))
           (select-keys oauth-scopes)))))

(filters/add-filter! :keys keys)


(parser/add-tag! :report-link
  (fn [[report] context]
    (let [lookup (lookup-args context)
          report (lookup report)
          request (:request context)]
      (util/route-path
        request
        (if (or (= "reviewed" (:status report))
                (= (get-in request [:identity :role]) "admin")
                (not (get-in request [:identity :review_allowed])))
          :staff.report/view
          :staff.report/review)
        {:id (:id report)}
        nil))))


(parser/add-tag! :report-link-v2
  (fn [[report] context]
    (let [lookup (lookup-args context)
          report (lookup report)
          request (:request context)]
      (util/route-path
        request
        (if (or (= "reviewed" (:status report))
                (= (get-in request [:identity :role]) "admin")
                (not (get-in request [:identity :review_allowed])))
          :staff.report/view
          :staff.report/review2)
        {:id       (:id report)}
        {:next_url (str (:uri request) "?" (:query-string request))}))))


(parser/add-tag! :video_title
  (fn [[extra_video extra_video_type] context]
    (let [lookup (lookup-args context)
          extra_video (lookup extra_video)
          extra_video_type (lookup extra_video_type)]
      (if (and extra_video (not extra_video_type))
        (t "Первое видео")
        (t "Видео")))))


(parser/add-tag! :report-menu-badge
  (fn [_ _]
    (let [restrict (resolve 'jarima.handler.staff.report/restrict-filter)
          count (-> {:status ["created"]}
                    (restrict (:identity *request*))
                    (q/select-report)
                    (assoc :select [[:%count.* :count]])
                    (dissoc :order-by)
                    (db/query-first :count))]
      (when (pos? count)
        (h/html
          [:span.kt-badge.kt-badge--brand.kt-badge--inline.kt-badge--pill.ml-1.mb-3
           {:style "padding: 0.65rem 0.45rem"
            :title (t "Ожидание рассмотрения")} count])))))


(parser/add-tag! :offense-menu-badge
  (fn [_ _]
    (let [restrict (resolve 'jarima.handler.staff.report/restrict-filter)
          count (-> {:status ["failed"]}
                    (restrict (:identity *request*))
                    (q/select-offense)
                    (assoc :select [[:%count.* :count]])
                    (dissoc :order-by)
                    (db/query-first :count))]
      (when (pos? count)
        (h/html
          [:span.kt-badge.kt-badge--warning.kt-badge--inline.kt-badge--pill.ml-1.mb-3
           {:style "padding: 0.65rem 0.45rem"
            :title (t "Не доставлен")} count])))))


(parser/add-tag! :reward-menu-badge
  (fn [_ _]
    (let [restrict (resolve 'jarima.handler.staff.report/restrict-filter)
          count (-> {:status ["failed"]}
                    (restrict (:identity *request*))
                    (q/select-reward)
                    (assoc :select [[:%count.* :count]])
                    (dissoc :order-by)
                    (db/query-first :count))]
      (when (pos? count)
        (h/html
          [:span.kt-badge.kt-badge--warning.kt-badge--inline.kt-badge--pill.ml-1.mb-3
           {:style "padding: 0.65rem 0.45rem"
            :title (t "Не доставлен")} count])))))


(do
  (parser/set-closing-tags! :with-render :do :end-with-render)
  (swap! expr-tags
         assoc :with-render
         (fn [[name] tag-content render rdr]
           (let [{expr :with-render body :do} (tag-content rdr :with-render :do :end-with-render)]
             (fn [context]
               (->> (render (:content expr) context)
                    (assoc context (keyword name))
                    (render (:content body))))))))


(parser/add-tag! :json-dump
  (fn [[name data] context]
    (let [lookup (lookup-args context)
          data (lookup data)]
      (str "<script>var " name " = " (json/encode data) "</script>"))))


(parser/add-tag! :warn-unsupported-card
  (fn [_ _]
    (when-let [card (-> *request* :identity :card not-empty)]
      (when-not (re-matches #"8600(?!34)\d{12}" card)
        (h/html
          [:div.alert.alert-danger.fade.show.mt-4.mb-0.w-100
           [:div.alert-icon [:i.flaticon-warning]]
           [:div.alert-text (t "Ваша карта от банка КДБ не поддерживаются. Пожалуйтса, укажите в профиле карту другого банка.")]])))))


(parser/add-tag! :substract
  (fn [args context]
    (let [lookup (lookup-args context)]
      (apply - (map #(let [n (or (lookup %) 0)]
                       (if (number? n)
                         n
                         (BigDecimal. n)))
                    args)))))
