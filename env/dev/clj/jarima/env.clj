(ns jarima.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [jarima.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[jarima started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[jarima has shut down successfully]=-"))})


(def wrap-env
  wrap-dev)
