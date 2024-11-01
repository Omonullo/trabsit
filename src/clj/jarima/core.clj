(ns jarima.core
  (:gen-class)
  (:import [java.util Locale])
  (:require
    [jarima.http]
    [jarima.nrepl]
    [jarima.webhook]
    [jarima.telegram]
    [jarima.encoder]
    [jarima.detector]
    [mount.core :as mount]
    [jarima.config :refer [env]]
    [jarima.env :refer [defaults]]
    [clojure.tools.logging :as log]
    [luminus-migrations.core :as migrations]))


(Locale/setDefault (Locale. "ru" "RU"))


(mount/defstate app
  :start ((or (:init defaults) identity))
  :stop ((or (:stop defaults) identity)))


(defn -main [& args]
  (doseq [component (:started (mount/start #'jarima.config/env))]
    (log/info component "started"))

  (->> (select-keys env [:database-url])
       (migrations/migrate ["migrate"]))

  (doseq [component (:started (if (:disable-runners env)
                                (mount/start (remove #(re-matches #".*(/|-)(ticker|runner)" %)
                                                     (mount/find-all-states)))
                                (mount/start)))]
    (log/info component "started"))

  (-> (Runtime/getRuntime)
      (.addShutdownHook
        (Thread.
          (fn []
            (doseq [component (:stopped (mount/stop))]
              (log/info component "stopped"))
            (shutdown-agents))))))
