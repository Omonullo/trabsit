(ns jarima.handler.admin.client
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require [jarima.util :as util]
            [jarima.db.core :as db]
            [medley.core :refer :all]
            [jarima.validation :as v]
            [jarima.spec :as spec]
            [jarima.db.query :as query]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]
            [jarima.db.query :as q]))


(defn create
  [request]
  (let [client (-> request
                   (get-in [:parameters :form])
                   (update :enabled boolean)
                   (update :encoding_required boolean)
                   (update :allowed_scope set)
                   (update :default_scope set))]
    (if-let [errors (v/validate-client-form (assoc client :creation true))]
      (layout/render "admin/client/form.html"
        {:client      (-> client
                          (update :allowed_scope set)
                          (update :default_scope set))
         :errors      errors
         :scopes      spec/oauth-scopes
         :grant_types spec/oauth-grant-types
         :creation    true}
        response/bad-request)
      (do
        (-> (query/insert-oauth-client client)
            (db/query))
        (-> (util/route-path request :admin.client/index)
            (response/found))))))


(defn edit
  [request]
  (let [client (-> request
                   (get-in [:parameters :form])
                   (update :enabled boolean)
                   (update :encoding_required boolean)
                   (update :allowed_scope set)
                   (update :default_scope set)
                   (update :redirect_uri #(some-> % seq distinct))
                   (update :error_redirect_uri identity)
                   (update :report_status_webhook identity)
                   (update :offense_status_webhook identity)
                   (update :url identity)
                   (update :logo identity))
        id (-> request :parameters :path :id)]
    (when-not (-> (query/select-oauth-client {:id [id]})
                  (db/query-first :id))
      (layout/error-page!
        {:status 404
         :title  (t "Статья не найдена")}))
    (if-let [errors (v/validate-client-form (assoc client :old-id id))]
      (-> (layout/render "admin/client/form.html"
            {:client      (-> client
                              (update :allowed_scope set)
                              (update :default_scope set))
             :errors      errors
             :scopes      spec/oauth-scopes
             :grant_types spec/oauth-grant-types}
            response/bad-request))
      (do
        (-> (query/update-oauth-client [id] client)
            (db/query))
        (-> (util/route-path request :admin.client/index)
            (response/found))))))


(defn edit-form
  [request]
  (let [client (-> (query/select-oauth-client {:id [(-> request :parameters :path :id)]})
                   (db/query-first))]
    (when-not client
      (layout/error-page!
        {:status 404
         :title  (t "Клиент не найден")}))
    (layout/render
      "admin/client/form.html"
      {:client      (-> client
                        (update :allowed_scope set)
                        (update :default_scope set))
       :grant_types spec/oauth-grant-types
       :scopes      spec/oauth-scopes})))




(defn index [_]
  (layout/render
    "admin/client/list.html"
    {:clients     (-> (q/select-oauth-client {})
                      (db/query))
     :grant_types spec/oauth-grant-types}))


(defn create-form
  [_]
  (layout/render "admin/client/form.html"
    {:creation    true
     :grant_types spec/oauth-grant-types
     :scopes      spec/oauth-scopes}))


(def routes
  ["/clients"
   [""
    {:name :admin.client/index
     :get  #'index}]

   ["/new"
    {:name       :admin.client/create
     :get        #'create-form
     :post       #'create
     :parameters {:form :admin.client/form}}]

   ["/:id/edit"
    {:name       :admin.client/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/nillable-uuid}
                  :form :admin.client/form}}]

   #_["/:id/delete"
      {:name       :admin.client/delete
       :post       #'delete
       :parameters {:path {:id :client/id}}}]])
