(ns jarima.handler.admin.article
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require [jarima.util :as util]
            [jarima.db.core :as db]
            [medley.core :refer :all]
            [jarima.validation :as v]
            [jarima.db.query :as query]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]))


(defn index
  [_]
  (layout/render
    "admin/article/list.html"
    {:articles
     (-> (query/select-article nil)
         (db/query))}))


(defn create-form
  [_]
  (layout/render "admin/article/form.html" {:creation true}))


(defn create
  [request]
  (let [article (-> request :parameters :form
                    (update :obsolete boolean)
                    (update :citizen_selection_enabled boolean))]
    (if-let [errors (v/validate-article article)]
      (layout/render "admin/article/form.html"
        {:article  article
         :errors   errors
         :creation true})
      (do
        (-> (query/insert-article article)
            (db/query))
        (-> (util/route-path request :admin.article/index)
            (response/found))))))


(defn edit-form
  [request]
  (let [article (-> (query/select-article {:id [(-> request :parameters :path :id)]})
                    (db/query-first))]
    (when-not article
      (layout/error-page!
        {:status 404
         :title  (t "Статья не найдена")}))
    (layout/render
      "admin/article/form.html"
      {:article article})))


(defn edit
  [request]
  (let [article (-> request :parameters :form
                    (update :obsolete boolean)
                    (update :citizen_selection_enabled boolean))
        id (-> request :parameters :path :id)]
    (when-not (-> (query/select-article {:id [id]})
                  (db/query-first :id))
      (layout/error-page!
        {:status 404
         :title  (t "Статья не найдена")}))
    (if-let [errors (v/validate-article (assoc article :old-id id))]
      (layout/render "admin/article/form.html"
        {:article article
         :errors  errors})
      (do (-> (query/update-article id article)
              (db/query))
          (-> (util/route-path request :admin.article/index)
              (response/found))))))



(defn delete
  [request]
  (-> (util/route-path request :admin.article/index)
      (response/found)
      (assoc :flash
             (when (zero? (db/delete :article (-> request :parameters :path :id)))
               {:alert {:warning (t "Ну удалось удалить статью")}}))))


(def routes
  ["/articles"
   [""
    {:name :admin.article/index
     :get  #'index}]

   ["/new"
    {:name       :admin.article/create
     :get        #'create-form
     :post       #'create
     :parameters {:form :admin.article/form}}]

   ["/:id/edit"
    {:name       :admin.article/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :article/id}
                  :form :admin.article/form}}]

   ["/:id/delete"
    {:name       :admin.article/delete
     :post       #'delete
     :parameters {:path {:id :article/id}}}]])
