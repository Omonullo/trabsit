(ns jarima.handler.admin.faq
  (:refer-clojure :exclude [boolean? ex-cause ex-message uuid?])
  (:require [jarima.db.core :as db]
            [jarima.db.query :as query]
            [medley.core :refer :all]
            [jarima.layout :as layout :refer [t]]
            [ring.util.http-response :as response]
            [jarima.util :as util]))


(defn categories
  []
  (let [faqs (db/query (query/select-faq nil))]
    {:categories_ru (->> faqs (map :category_ru) distinct)
     :categories_uz_cy (->> faqs (map :category_uz_cy) distinct)
     :categories_uz_la (->> faqs (map :category_uz_la) distinct)}))


(defn index
  [_]
  (layout/render
    "admin/faq/list.html"
    {:faqs (-> (query/select-faq nil)
               (db/query))}))


(defn create-form
  [_]
  (layout/render
    "admin/faq/form.html"
    (categories)))


(defn create
  [request]
  (-> (query/insert-faq (-> request :parameters :form (assoc :id (random-uuid))))
      (db/query))
  (-> (util/route-path request :admin.faq/index)
      (response/found)))


(defn edit-form
  [request]
  (let [faq
        (-> (query/select-faq {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not faq
      (layout/error-page!
        {:status 404
         :title  (t "Вопрос не найден")}))
    (layout/render
      "admin/faq/form.html"
      (-> (categories)
          (assoc :faq faq)))))


(defn edit
  [request]
  (let [faq
        (-> (query/select-faq {:id [(-> request :parameters :path :id)]})
            (db/query-first))]
    (when-not faq
      (layout/error-page!
        {:status 404
         :title  (t "Вопрос не найден")}))
    (-> (query/update-faq (:id faq) (-> request :parameters :form))
        (db/query))
    (-> (util/route-path request :admin.faq/index)
        (response/found))))


(defn delete
  [request]
  (-> (util/route-path request :admin.faq/index)
      (response/found)
      (assoc :flash
             (when (zero? (db/delete :faq (-> request :parameters :path :id)))
               {:alert {:warning (t "Ну удалось удалить вопрос")}}))))


(def routes
  ["/faqs"
   [""
    {:name :admin.faq/index
     :get  #'index}]

   ["/new"
    {:name :admin.faq/create
     :get  #'create-form
     :post #'create
     :parameters {:form :admin.faq/form}}]

   ["/:id/edit"
    {:name       :admin.faq/edit
     :get        #'edit-form
     :post       #'edit
     :parameters {:path {:id :jarima.spec/uuid}
                  :form :admin.faq/form}}]

   ["/:id/delete"
    {:name       :admin.faq/delete
     :post       #'delete
     :parameters {:path {:id :jarima.spec/uuid}}}]])
