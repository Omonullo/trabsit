(ns jarima.middleware
  (:require
    [raven-clj.ring]
    [jarima.util :as util]
    [jarima.db.core :as db]
    [jarima.db.query :as q]
    [clojure.string :as str]
    [ring.middleware.cors :as cors]
    [jarima.validation :as v]
    [muuntaja.core :as muuntaja]
    [jarima.env :refer [wrap-env]]
    [medley.core :refer [map-keys uuid]]
    [clojure.tools.logging :as log]
    [jarima.redis :refer [*redis*]]
    [buddy.auth :refer [authenticated?]]
    [compojure.route :refer [resources]]
    [buddy.auth.protocols :as auth.proto]
    [ring.util.http-response :as response]
    [jarima.config :refer [env dictionary]]
    [medley.core :refer [assoc-some map-vals]]
    [buddy.auth.accessrules :refer [restrict]]
    [ring.middleware.flash :refer [wrap-flash]]
    [jarima.layout :refer [error-page *request*]]
    [ring.middleware.accept :refer [wrap-accept]]
    [taoensso.carmine.ring :refer [carmine-store]]
    [ring.middleware.cookies :refer [wrap-cookies]]
    [ring.middleware.session :refer [wrap-session]]
    [ring.middleware.anti-forgery :as ring.middleware]
    [muuntaja.middleware :refer [wrap-format wrap-params]]
    [buddy.auth.backends.session :refer [session-backend]]
    [ring.middleware.session.memory :refer [memory-store]]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
    [ring.middleware.multipart-params.temp-file :refer [temp-file-store]]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [jarima.layout :as layout]))



(defn key-backend []
  (reify
    auth.proto/IAuthentication
    (-parse [_ request]
      (or (some->> (get-in request [:headers "authorization"] "")
                   (re-find (re-pattern "^Bearer (.+)$"))
                   (second))
          (get-in request [:params :token])))
    (-authenticate [_ req token]
      (util/cond-let
        (= (get-in env [:asbt :token]) token)
        {:role "asbt"}

        :let [oauth (-> {:access_token token}
                        (q/select-oauth)
                        (db/query-first))]
        (= (:client_grant_type oauth) "code")
        (-> {:id [(:token_citizen_id oauth)]}
            (q/select-citizen)
            (db/query-first)
            (assoc :role "oauth_code"
                   :oauth_client oauth))

        (= (:client_grant_type oauth) "client_credentials")
        (-> {:id [(some-> req
                          (get-in [:headers "citizen-id"])
                          (not-empty)
                          (uuid))]}
            (q/select-citizen)
            (db/query-first)
            (assoc :role "oauth_client_credentials"
                   :oauth_client oauth))))


    auth.proto/IAuthorization
    (-handle-unauthorized [_ _ _]
      (response/forbidden {:error "Доступ запрещен"}))))


(defn wrap-code-scope [& scopes]
  (fn [handler]
    (fn [req]
      (let [client (:oauth_client (:identity req))
            errors (-> (:oauth_client (:identity req))
                       (assoc :resource_scope (map name scopes))
                       (v/validate-oauth-scope))]
        (if (and (= (:client_grant_type client) "code") errors)
          (response/forbidden (util/->error-response errors))
          (handler req))))))


(defn wrap-oauth-client-request [handler]
  (fn [req]
    (if-let [errors (-> (:oauth_client (:identity req))
                        (v/validate-oauth-client-request))]
      (let [response-fn (if (= :invalid_token (first (ffirst errors)))
                          response/unauthorized
                          response/forbidden)]
        (response-fn (util/->error-response errors)))
      (handler req))))


(defn wrap-oauth-citizen [handler]
  (fn [req]
    (if (nil? (:id (:identity req)))
      (response/forbidden {:error             "invalid-citizen-id"
                           :error_description (jarima.layout/t "Гражданин не указан")
                           :errors            {:citizen-id [(jarima.layout/t "Гражданин не найден")]}})
      (handler req))))


(defn wrap-auth-key
  [handler]
  (-> handler
      (wrap-authorization (key-backend))
      (wrap-authentication (key-backend))))

(defn service-authfn
  [req {:keys [username password]}]
  (when (and (= username (:service-login env))
             (= password (:service-password env)))
    (keyword username)))


(def service-auth-backend
  (http-basic-backend {:authfn service-authfn}))


(defn wrap-basic-auth
  [handler]
  (-> handler
      (wrap-authorization service-auth-backend)
      (wrap-authentication service-auth-backend)))


(defn wrap-anti-forgery [handler]
  (ring.middleware/wrap-anti-forgery
    handler
    {:error-handler
     (fn [_]
       (error-page {:status 403
                    :title  "Неправильный anti-forgery токен"}))}))


(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        (error-page {:status  500
                     :title   "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))


(defn wrap-locale [handler]
  (fn [request]
    (let [locale (->> (util/available-locales (:translations dictionary))
                      (util/request-locale request))]
      (-> request
          (assoc :locale locale)
          (handler)
          (assoc-some :cookies
                      (when-not (= (get-in request [:cookies "locale" :value]) (name locale))
                        {"locale" {:path "/" :value (name locale)}}))))))


(defn wrap-disabled-alerts [handler]
  (fn [request]
    (-> request
        (assoc :disabled_alerts
               (-> request
                   (get-in [:cookies "disabled_alerts" :value] "")
                   (str/split #"\|")
                   (set)))
        (assoc :flash {:alert {:success "wtf?"}})
        (handler))))


(defn wrap-formats [handler]
  (-> handler
      (wrap-params)
      (wrap-format (muuntaja/create))))


(defn wrap-binding-request [handler]
  (fn [req]
    (binding [*request* req]
      (when-not (:prod env)
        (alter-var-root
          #'jarima.layout/last-request
          (constantly req)))
      (handler req))))


(defn wrap-sentry [handler]
  (raven-clj.ring/wrap-sentry
    handler
    (:sentry-dsn env)
    {:namespaces  ["jarima"]
     :environment (when (:prod env) "production")}))


(defn wrap-citizen-area [handler]
  (restrict handler {:handler (comp boolean #{"citizen"} :role :identity)}))


(defn wrap-inspector-area [handler]
  (restrict handler {:handler (comp boolean #{"inspector"} :role :identity)}))


(defn wrap-review-area [handler]
  (restrict
    handler
    {:handler
     (fn [request]
       (or (-> request :identity :review_allowed boolean)
           (-> request :identity :role #{"admin"} boolean)))}))


(defn wrap-staff-area [handler]
  (restrict handler {:handler (comp boolean #{"inspector" "admin"} :role :identity)}))


(defn maintenance-middleware [handler]
  (fn [request]
    (let [now               (util/now)
          from              (util/parse-local-date-time-short (:from (:maintenance env)))
          to                (util/parse-local-date-time-short (:to (:maintenance env)))
          maintenance-time? (and
                              (or (.isEqual now from)
                                  (.isAfter now from))
                              (or (.isEqual now to)
                                  (.isBefore now to)))]
      (if maintenance-time?
        (layout/error-page!
          {:status 503
           :title  (layout/t "Технические работы")
           :data   (layout/t "В связи запланированными техническими работами, сервис dyhxx.ejarima.uz временно приостановит загрузку видео в целях модернизации. Надеемся на ваше понимание. <br/> <br/> Технические работы не затронут выплату вознаграждений и оплату штрафов.")})
        (handler request)))))


(defn wrap-citizen-upload-permission [handler]
  (fn [request]

    (let [citizen (-> {:id [(:id (:identity request))]}
                      (q/select-citizen)
                      (db/query-first))]
      (if (:upload_forbidden citizen)
        (layout/error-page!
          {:status 403
           :title  (layout/t "Доступ запрещён")
           :data   (layout/t "Ваш аккаунт заблокирован для загрузки новых видео")})
        (handler request)))))


(defn wrap-admin-area [handler]
  (restrict handler {:handler (comp boolean #{"admin"} :role :identity)}))


(defn wrap-session-auth [handler]
  (let [backend
        (session-backend
          {:authfn
           (fn [identity]
             (if (= "citizen" (:role identity))
               (-> {:from   [:citizen]
                    :select [:* ["citizen" :role]]
                    :where  [:= :id (:id identity)]}
                   (db/query)
                   (first))
               (-> {:from   [:staff]
                    :select [:*]
                    :where  [:and [:= true :active] [:= :id (:id identity)]]}
                   (db/query)
                   (first))))

           :unauthorized-handler
           (fn [request _]
             (if (authenticated? request)
               (->> {:status  403
                     :refresh [1 "/"]
                     :title   (str "Доступ запрещен")}
                    (error-page))
               (->> {:next_url (str (:uri request) "?" (:query-string request))}
                    (util/route-path request :misc/login nil)
                    (response/found))))})]
    (-> handler
        (wrap-authorization backend)
        (wrap-authentication backend))))


(defn wrap-resource
  [handler]
  (let [resources-handler (resources "/" {:root "public"})]
    (fn [request]
      (or (resources-handler request)
          (handler request)))))


(defn wrap-multipart-as-form-params
  [handler]
  (fn [request]
    (handler
      (if (:multipart-params request)
        (update request :form-params merge (:multipart-params request))
        request))))


(def nillable-temp-file-store
  "Returns nil if uploaded file is empty"
  (let [store (temp-file-store)]
    (fn [item]
      (let [result (store item)]
        (when (pos? (:size result))
          result)))))

(defn create-cors-middleware [origins]
  (fn [handler]
    (cors/wrap-cors
      handler
      :access-control-allow-origin origins
      :access-control-allow-methods [:get :post :put :delete :options])))


(defn wrap-bearer-session [handler]
  (fn [request]
    (if-let [token (get-in request [:headers "authorization"])]
      (let [citizen (-> {:select [:*]
                         :from   [:citizen]
                         :where  [:= :token token]}
                        (db/query)
                        (first))]
        (-> (when (some? citizen)
              {:identity (assoc citizen :role "citizen"
                                        :enterprise true)})
            (merge request)
            (handler)))
      (handler request))))


(defn wrap-base [handler]
  (-> handler
      (wrap-binding-request)
      (wrap-sentry)
      (wrap-flash)
      (wrap-cookies)
      (wrap-session {:store (carmine-store *redis* :expiration-secs (get env :session-expire-secs 1200))})
      (wrap-bearer-session)
      (wrap-env)
      (wrap-resource)
      (wrap-locale)
      (wrap-disabled-alerts)
      (wrap-content-type)
      (wrap-multipart-as-form-params)
      (wrap-accept {:mime ["application/json" :as :json, "text/html" :as :html] :language ["ru" :as :ru, "uz" :as :uz_cy]})
      (wrap-defaults
        (-> site-defaults
            (assoc :session false)
            (assoc-in [:security :anti-forgery] false)
            (assoc-in [:params :multipart] {:store nillable-temp-file-store})))
      (wrap-forwarded-remote-addr)
      (wrap-internal-error)))
