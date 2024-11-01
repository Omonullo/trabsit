(ns jarima.redis
  (:require
    [mount.core :as mount]
    [taoensso.carmine :as car]
    [jarima.config :refer [env]]
    [taoensso.carmine.locks :as locks]))


(mount/defstate ^:dynamic *redis*
  :start {:pool {} :spec {:uri (:redis-url env)}})


(defmacro wcar [& body]
  `(car/wcar *redis* ~@body))


(defn set-confirmation-code
  [phone]
  (let [key (str "confirmation-code:" phone)
        [code ttl] (wcar (car/get key) (car/ttl key))]
    (if (some? code)
      [code ttl false]
      (let [code (format "%06d" (rand-int 1e6))
            ttl  (:confirmation-code-expire env 120)]
        (wcar (car/set key code :ex ttl))
        [code ttl true]))))


(defn get-confirmation-code
  [phone]
  (wcar (car/get (str "confirmation-code:" phone))))

(defn delete-confirmation-code! [phone]
  (wcar (car/del (str "confirmation-code:" phone))))




(defn get-temp-video
  [id]
  (wcar (car/hmget "temp_videos" id)))


(defn add-temp-video
  [id video]
  (wcar (car/hmset "temp_videos" id video)))


(defn delete-temp-video [id]
  (wcar (car/hdel "temp_videos" id)))


(defn list-temp-videos []
  (wcar (car/hvals "temp_videos")))


(defmacro locking-video [& body]
  `(:result (locks/with-lock *redis* "temp_video" 7000 6000 ~@body)))


;;  oauth

(defn set-oauth-token
  [oauth]
  (let [key (str "oauth:code:" (:code oauth))]
    (let [ttl (:authorization-code-expire env 120)]
      (wcar (car/set key oauth :ex ttl))
      [oauth ttl true])))


(defn get-token-by-code [code]
  (wcar (car/get (str "oauth:code:" code))))


(defn delete-oauth [code]
  (wcar (car/del (str "oauth:code:" code))))
