(ns jarima.handler.oauth
  (:require [clojure.set :as set]
            [jarima.util :as util]
            [jarima.spec :as spec]
            [jarima.db.query :as q]
            [jarima.db.core :as db]
            [jarima.redis :as redis]
            [jarima.validation :as v]
            [jarima.layout :as layout]
            [jarima.config :refer [env]]
            [medley.core :refer [random-uuid]]
            [jarima.middleware :as middleware]
            [ring.util.http-response :as response]
            [medley.core :refer [update-existing map-keys assoc-some remove-vals]]
            [cheshire.core :as json]))


(defn store-token-with-code [oauth]
  (let [code (util/uuid->guid (random-uuid))]
    (redis/set-oauth-token (assoc oauth :code code))
    code))


(defn scope-match-role [role scope]
  (->>
    (get spec/oauth-scopes role)
    (keys)
    (set)
    (set/intersection (set scope))))


(defn wrap-oauth-request
  [handler]
  (fn [req]
    (let [form (->> (merge
                      (-> req :parameters :query)
                      (-> req :parameters :form)))
          client (-> {:id [(:client_id form)]}
                     (q/select-oauth-client)
                     (db/query-first)
                     ;; Reduce scope to what is available for that particular role
                     (update-existing :allowed_scope (partial scope-match-role (-> req :identity :role)))
                     (update-existing :default_scope (partial scope-match-role (-> req :identity :role))))
          auth_request (-> form
                           (assoc :stored_client client)
                           (assoc :scope (or (:scope form) (:default_scope client))))
          redirect-uri-params (some->
                                (or (:redirect_uri form)
                                    (first (:redirect_uri client)))
                                (util/split-uri-query)
                                (second))]
      (if-let [errors (v/validate-oauth-request
                        (update auth_request :redirect_uri #(some-> % util/remove-uri-query)))]
        (if (or (= :client_id (last (ffirst errors))) (:debug form))
          (layout/error-page {:status 400 :data (util/pretty-errors errors)})
          ;; redirecting to client
          (as-> (util/->error-response errors) x
                (update x :errors json/encode)
                (assoc x :state (:state form))
                (util/add-uri-query (:error_redirect_uri client)
                                    (merge redirect-uri-params x))
                (response/found x)))
        (handler (assoc req :auth_request auth_request))))))

;; has tests
(defn approve [req]
  (let [user (:identity req)
        auth_request (:auth_request req)
        stored_client (:stored_client auth_request)
        redirect_uri (or (:redirect_uri auth_request)
                         (first (:redirect_uri stored_client)))]
    (assert (= (:grant_type stored_client) "code"))
    (assert (= (:grant_type stored_client) (:response_type auth_request)))
    (->> {:state (:state auth_request)
          :code  (-> auth_request
                     (assoc :user_id (:id user)
                            :user_role (:role user))
                     (store-token-with-code))}
         (util/add-uri-query redirect_uri)
         (response/found))))


(defn approve-form [req]
  (let [user (:identity req)
        auth_request (:auth_request req)
        oauth-token (-> {:user_id             [(:id user)]
                         :client_id           [(:client_id auth_request)]
                         :scope               (:scope auth_request)
                         :enabled             true
                         :revoked             false
                         :refresh_expire_time {:gt (util/now)}}
                        (q/select-oauth-token)
                        (db/query-first))]
    ;; check if scope is already granted
    (if (some? oauth-token)
      (approve req)
      (layout/render "oauth/approve.html"
        {:scopes       (->> (:scope auth_request)
                            (select-keys (-> (:role user) spec/oauth-scopes))
                            (map (fn [[id scope]] (assoc scope :id id))))
         :auth_request auth_request}))))


(defn authorize [req]
  (let [{force-login :login} (-> req :parameters :query)]
    (if (and (-> req :identity) (not force-login))
      (response/found (util/route-path req :oauth/approve nil (:query-params req)))
      (response/found (util/route-path req :oauth/login nil
                                       {:next_url (util/route-path
                                                    req :oauth/approve
                                                    nil (:query-params req))})))))


(defn wrap-client-credentials
  [handler]
  (fn [request]
    (let [form (-> request :parameters :params)
          client (-> {:id     [(:client_id form)]
                      :secret (:client_secret form)}
                     (q/select-oauth-client)
                     (db/query-first))]
      (if-let [errors (-> form
                          (assoc :stored_client client)
                          (v/validate-oauth-client-credentials))]

        (response/bad-request (util/->error-response errors))
        (handler (assoc request :stored_client client))))))

;; has tests
(defn issue [req]
  (let [form (-> (get-in req [:parameters :params])
                 (update-existing :redirect_uri util/remove-uri-query))
        stored-client (:stored_client req)
        stored-token (-> (redis/get-token-by-code (:code form))
                         (update-existing :redirect_uri #(some-> % util/remove-uri-query)))]
    (if-let [errors (-> (assoc form :stored_token stored-token
                                    :stored_client stored-client)
                        (v/validate-oauth-access-token))]
      (-> (util/->error-response errors)
          (response/bad-request))

      (let [access-token (util/uuid->guid (random-uuid))
            refresh-token (util/uuid->guid (random-uuid))
            id (random-uuid)
            user-key (some-> stored-token :user_role (str "_id") (keyword))
            access-expire-in (:access_token_expire_seconds env (* 24 60 60))
            refresh-expire-in (:refresh_token_expire_seconds env (* 24 60 60 30))
            now (util/now)]
        ;; Revoke previously issued tokens for this user
        (-> {:client_id           [(:id stored-client)]
             :refresh_expire_time {:gt (util/now)}
             :revoked             false}
            (assoc-some user-key (when-let [user-id (:user_id stored-token)] [user-id]))
            (q/update-oauth-token {:revoked true})
            (db/query-first))

        (-> {:id                  id
             :client_id           (:id stored-client)
             :access_token        access-token
             :refresh_token       refresh-token
             :create_time         now
             :refresh_time        now
             :grant_type          (:grant_type stored-client)
             :access_expire_time  (.plusSeconds now access-expire-in)
             :refresh_expire_time (.plusSeconds now refresh-expire-in)}
            (assoc-some user-key (:user_id stored-token)
                        :scope (:scope stored-token))
            (q/insert-oauth-token)
            (db/query-first))

        (redis/delete-oauth (:code form))
        (-> {:access_token       access-token
             :refresh_token      refresh-token
             :token_type         "Bearer"
             :expires_in         access-expire-in
             :refresh_expires_in refresh-expire-in}
            (assoc-some :scope (:scope stored-token))
            (response/ok))))))

;; has tests
(defn refresh [req]
  (let [form (-> req :parameters :params)
        oauth-token (-> {:refresh_token (:refresh_token form)
                         :client_id     [(:client_id form)]}
                        (q/select-oauth-token)
                        (db/query-first))]
    (if-let [errors (-> (assoc form :stored_token oauth-token)
                        (v/validate-oauth-refresh-token))]
      (response/bad-request (util/->error-response errors))
      (let [access-token (util/uuid->guid (random-uuid))
            refresh-token (util/uuid->guid (random-uuid))
            access-expire-in (:access_token_expire_seconds env (* 24 60 60))
            now (util/now)
            oauth (-> (q/update-oauth-token
                        {:id [(:id oauth-token)]}
                        {:access_expire_time (.plusSeconds now access-expire-in)
                         :access_token       access-token
                         :refresh_time       now
                         :scope              (or (:scope form) (:scope oauth-token))
                         :refresh_token      refresh-token})
                      (db/query-first))]
        (response/ok {:access_token       access-token
                      :refresh_token      refresh-token
                      :token_type         "Bearer"
                      :expires_in         access-expire-in
                      :refresh_expires_in (util/secondsBetween (:refresh_expire_time oauth) now)
                      :scope              (:scope oauth)})))))


(defn login-form [_]
  (layout/render "oauth/login.html"))


(defn revoke [req]
  (let [form (-> req :parameters :form)
        user (:identity req)]
    (-> (util/route-path req :citizen.apps/list)
        (response/found)
        (assoc :flash
               (when-not (seq (-> (q/update-oauth-token {:id [(:token_id form)]
                                                         :user_id  [(:id user)]}
                                                        {:revoked true})
                                  (db/query)))
                 {:alert {:warning (layout/t "Ну удалось удалить статью")}})))))


(def routes
  [""
   ["/authorize"
    {:name       :oauth/authorize
     :parameters {:query :oauth.authorize/query}
     :get        #'authorize}]
   ["/login"
    {:name       :oauth/login
     :parameters {:query :oauth.authorize/query}
     :get        #'login-form}]
   ["/approve"
    {:name       :oauth/approve
     ;; TODO allow staff to use oauth
     :middleware [middleware/wrap-citizen-area
                  wrap-oauth-request]
     :parameters {:query :oauth.authorize/query
                  :form  :oauth.authorize/form}
     :get        #'approve-form
     :post       #'approve}]
   ["/revoke"
    {:name       :oauth/revoke
     :middleware [middleware/wrap-citizen-area]
     :parameters {:form :oauth.token/revoke}
     :post       #'revoke}]])


(def api-routes
  [""
   {:middleware [wrap-client-credentials]}
   ["/access_token"
    {:name       :oauth/access_token
     :parameters {:params :oauth.access_token/form}
     :post       #'issue}]
   ["/refresh_token"
    {:name       :oauth/refresh_token
     :parameters {:params :oauth.refresh_token/form}
     :post       #'refresh}]])
