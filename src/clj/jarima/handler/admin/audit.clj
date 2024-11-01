(ns jarima.handler.admin.audit
  (:require
    [jarima.layout :as layout]
    [jarima.util :as util]))


(defn index
  [request]
  (let [page (-> request :parameters :query :page (or 1))
        size 100
        rows (util/read-log "log/audit.log" size (* size (dec page)))]
    (layout/render
      "admin/audit/index.html"
      {:page       page
       :size       size
       :rows       rows
       :last-page? (not= (count rows) size)})))


(def routes
  ["/audit"
   {:name       :admin.audit/index
    :get        #'index
    :parameters {:query :admin.audit.index/query}}])
