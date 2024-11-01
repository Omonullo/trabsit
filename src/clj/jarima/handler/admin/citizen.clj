(ns jarima.handler.admin.citizen
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require
    [jarima.sms :as sms]
    [jarima.util :as util]
    [jarima.db.core :as db]
    [clojure.string :as str]
    [medley.core :refer :all]
    [jarima.db.query :as query]
    [jarima.db.query :as query]
    [jarima.layout :as layout :refer [t]]
    [medley.core :refer [update-existing]]
    [ring.util.http-response :as response]
    [jarima.config :refer [dictionary env]]
    [jarima.validation :as v]
    [buddy.hashers :as hashers]))


(defn index
  [{{query :query} :parameters}]
  (layout/render
    "admin/citizen/list.html"
    (-> (partial query/select-citizen-with-reward
                 (util/vectorify-vals
                   query
                   (complement #{:q})))
        (util/paged-query (:page query)))))


(defn edit-form
  [request]
  (if-let [citizen
           (-> (query/select-citizen {:id [(-> request :parameters :path :id)]})
               (db/query-first))]
    (layout/render
      "admin/citizen/form.html"
      {:citizen citizen})
    (layout/error-page!
      {:status 404
       :title  (t "Пользователь не найден")})))


(defn edit
  [request]
  (let [form (merge (get-in request [:parameters :form])
                    (get-in request [:parameters :path]))]
    (if-let [citizen (-> {:id [(:id form)]}
                         (query/select-citizen)
                         (db/query-first))]
      (if-let [errors (v/validate-citizen-edit form)]
        (layout/render
          "admin/citizen/form.html"
          {:citizen citizen
           :form    form
           :errors  errors})
        (do
          (-> (query/update-citizen
                {:id [(:id citizen)]}
                (cond-> form
                        (and (:has_password form) (not-empty (:new_password form)))
                        (assoc :password (hashers/derive (:new_password form)))
                        (not (:has_password form))
                        (assoc :password nil)
                        true
                        (dissoc :id :has_password :new_password)
                        true
                        (update :upload_forbidden boolean)))
              (db/query))
          (when (and (:has_password form) (not-empty (:new_password form)))
            (sms/send (:phone form) (t "Ваш пароль от сайта dyhxx.ejarima.uz был изменён на \"%s\"" (:new_password form))))
          (-> (util/route-path request :admin.citizen/index)
              (response/found))))
      (layout/error-page!
        {:status 404
         :title  (t "Пользователь не найден")}))))



(def routes
  ["/citizens"
   [""
    {:name       :admin.citizen/index
     :get        #'index
     :parameters {:query :admin.citizen.index/query}}]

   ["/:id/edit"
    {:name       :admin.citizen/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/uuid}
                  :form :admin.citizen/form}}]])
