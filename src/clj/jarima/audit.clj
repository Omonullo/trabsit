(ns jarima.audit
  (:require
    [jarima.spec :as spec]
    [clojure.string :as str]
    [clojure.tools.logging :as log]))


(defn region
  [& parts]
  (or (some->> parts
               (remove nil?)
               (str/join ", ")
               (not-empty)
               (format "из региона %s"))
      ""))


(defn auth-success [remote-addr user-agent username role area district]
  (log/info (format "Пользователь %s c ролью %s %s успешно зашел с ip-адреса %s и браузера %s"
                    username
                    (spec/roles role)
                    (region area district)
                    remote-addr
                    user-agent)))


(defn auth-failure
  [remote-addr user-agent username]
  (log/info (format "Пользователь %s не смог зайти с ip-адреса %s и браузера %s"
                    username
                    remote-addr
                    user-agent)))

