(ns jarima.env
  (:require [clojure.tools.logging :as log]
            [jarima.prod-middleware :refer [wrap-prod]]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[jarima started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[jarima has shut down successfully]=-"))})


(def wrap-env
  wrap-prod)
