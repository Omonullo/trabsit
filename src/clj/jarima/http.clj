(ns jarima.http
  (:require
    [jarima.spec]
    [reitit.coercion]
    [reitit.ring :as ring]
    [mount.core :as mount]
    [jarima.util :as util]
    [expound.alpha :as expound]
    [jarima.config :refer [env]]
    [reitit.core :refer [expand]]
    [luminus.http-server :as http]
    [jarima.handler.misc :as misc]
    [jarima.handler.oauth :as oauth]
    [jarima.handler.service :as service]
    [jarima.handler.asbt :as asbt]
    [jarima.middleware :as middleware]
    [jarima.layout :refer [error-page]]
    [reitit.ring.coercion :as coercion]
    [jarima.handler.admin.faq :as admin.faq]
    [reitit.coercion.spec :as spec-coercion]
    [jarima.handler.admin.area :as admin.area]
    [jarima.handler.admin.staff :as admin.staff]
    [jarima.handler.admin.audit :as admin.audit]
    [jarima.handler.staff.report :as staff.report]
    [jarima.handler.staff.reward :as staff.reward]
    [jarima.handler.admin.reward :as admin.reward]
    [jarima.handler.admin.transfer :as admin.transfer]
    [jarima.handler.admin.config :as admin.config]
    [jarima.handler.admin.citizen :as admin.citizen]
    [reitit.ring.middleware.exception :as exception]
    [jarima.handler.admin.article :as admin.article]
    [jarima.handler.admin.offense-type :as admin.offense-type]
    [jarima.handler.staff.offense :as staff.offense]
    [jarima.handler.citizen.offense :as citizen.offense]
    [jarima.handler.api.report :as api.report]
    [jarima.handler.api.citizen :as api.citizen]
    [jarima.handler.citizen.tokens :as citizen.tokens]
    [jarima.handler.citizen.report :as citizen.report]
    [jarima.handler.admin.district :as admin.district]
    [jarima.handler.staff.response :as staff.response]
    [jarima.handler.admin.client :as admin.client]
    [jarima.handler.citizen.profile :as citizen.profile]
    [jarima.handler.staff.statistics :as staff.statistics]
    [jarima.handler.inspector.article :as inspector.article]
    [ring.middleware.http-response :refer [wrap-http-response]]
    [jarima.handler.citizen.organization :as citizen.organization]
    [clojure.set :as set]))


(defn coercion-error-handler
  [status message]
  (let [printer (expound/custom-printer {:theme :none, :print-specs? false})]
    (fn [exception request]
      (if (= :json (-> request :accept :mime))
        (let [errors (->> (ex-data exception)
                          :problems
                          :clojure.spec.alpha/problems
                          (filter #(-> % :path last (not= :clojure.spec.alpha/nil %)))
                          (map (fn [details]
                                 {(first (:path details)) #{(str "Coercion error "
                                                                 (some-> details :via (last) (name)))}}))
                          (apply merge-with set/union))]
          {:status status
           :body   {:error             "invalid_request"
                    :error-description (and (ffirst errors)
                                            (util/pretty-errors errors))
                    :errors            (and (ffirst errors)
                                            errors)}})
        (error-page
          {:status status
           :title  message
           :data   (with-out-str
                     (printer (-> exception ex-data :problems))
                     (flush))})))))


(defn routes []
  [""
   {:coercion   spec-coercion/coercion
    :middleware [middleware/wrap-formats
                 wrap-http-response
                 (exception/create-exception-middleware
                   {::exception/default                (fn [e _] (throw e))
                    :reitit.ring/response              exception/http-response-handler
                    :muuntaja/decode                   (fn [& _] (error-page {:status 400 :title "Плохой запрос"}))
                    :reitit.coercion/request-coercion  (coercion-error-handler 400 "Плохой запрос")
                    :reitit.coercion/response-coercion (coercion-error-handler 500 "Произошло что-то очень плохое!")})
                 middleware/wrap-session-auth
                 coercion/coerce-request-middleware
                 coercion/coerce-response-middleware
                 middleware/wrap-binding-request]}
   [""
    {:middleware [middleware/wrap-auth-key]}
    asbt/routes]


   ["/public-api" {:middleware []}
    staff.statistics/public-api-routes]

   ["/api"
    ["/staff"
     staff.statistics/api-routes
     staff.report/api-routes]

    ["/citizen" {:middleware [middleware/wrap-citizen-area]}
     citizen.report/api-routes]

    ["/oauth" {:middleware [(middleware/create-cors-middleware [#".*"])
                            middleware/wrap-auth-key
                            middleware/wrap-oauth-client-request]}
     api.report/routes
     api.citizen/routes]]

   ["/service"
    {:middleware [middleware/wrap-basic-auth]}
    service/routes]

   ["/oauth" {:middleware [(middleware/create-cors-middleware [#".*"])]}
    oauth/api-routes]

   [""
    {:middleware [middleware/wrap-anti-forgery]}
    misc/routes
    ["/oauth" oauth/routes]
    ["/inspector"
     {:middleware [middleware/wrap-inspector-area
                   middleware/wrap-anti-forgery]}
     inspector.article/routes]
    ["/staff"
     {:middleware [middleware/wrap-anti-forgery]}
     staff.report/routes
     staff.reward/routes
     staff.offense/routes
     staff.response/routes
     staff.statistics/routes]
    [""
     {:middleware [middleware/wrap-citizen-area]}
     citizen.report/routes
     citizen.offense/routes
     citizen.tokens/routes
     citizen.organization/routes
     citizen.profile/routes]

    ["/admin"
     {:middleware [middleware/wrap-admin-area]}
     admin.reward/routes
     admin.transfer/routes
     admin.client/routes
     admin.faq/routes
     admin.config/routes
     admin.audit/routes
     admin.area/routes
     admin.district/routes
     admin.article/routes
     admin.offense-type/routes
     admin.citizen/routes
     admin.staff/routes]]])


(defn not-found
  [_])


(defn expand-var-handler
  [data opts]
  (expand
    (if (var? data) {:handler data} data)
    opts))


(mount/defstate app
  :start
  (ring/ring-handler
    (ring/router
      (routes)
      {:expand expand-var-handler
       :reitit.coercion/parameter-coercion
       (assoc reitit.coercion/default-parameter-coercion
         :params (reitit.coercion/->ParameterCoercion :params :string true true))})
    (ring/routes
      (ring/redirect-trailing-slash-handler {:method :strip})
      (ring/create-default-handler
        {:not-found          (fn [& _] (error-page {:status 404 :title "Страница не найдена"}))
         :method-not-allowed (fn [& _] (error-page {:status 405 :title "Метод не поддерживается."}))
         :not-acceptable     (fn [& _] (error-page {:status 406 :title "Невозможно принять запрос."}))}))
    {:middleware [middleware/wrap-base]}))


(mount/defstate ^{:on-reload :noop} server
  :start
  (http/start
    (-> env
        (assoc :handler #'app)
        (assoc :io-threads (* 2 (util/available-processors)))))
  :stop
  (http/stop server))
