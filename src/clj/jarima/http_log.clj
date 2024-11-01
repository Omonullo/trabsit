(ns jarima.http-log
  (:require [clojure.string :as str]
            [jarima.config :refer [dictionary env]]
            [buddy.core.codecs :refer [hex->bytes bytes->str]])
  (:import (org.apache.logging.log4j LogManager)
           (org.apache.logging.log4j.core Appender LogEvent)))


(def ^:dynamic *logs* nil)


(defn- log->map
  [^LogEvent e]
  (let [message (str (.getMessage e))]
    (some-> message
            (str/replace "[\\r][\\n]" "")
            (str/replace "[\\n]" "")
            (str/replace #"(\[0x[a-f0-9]+])+"
                         #(-> (first %)
                              (str/replace #"\[0x([a-f0-9]+)]" (fn [[_ x]] (if (zero? (mod (count x) 2)) x (str "0" x))))
                              (hex->bytes)
                              (bytes->str))))))


(defn custom-appender [thread-id]
  (reify Appender
    (isStarted [_] true)
    (isStopped [_] true)
    (getName [_] (str "custom-appender-" thread-id))
    (append [_ event]
      (when (= thread-id (.getThreadId event))
        (swap! *logs* conj (log->map event))))))


(defn capture-logs [f]
  (let [appender (custom-appender (.getId (Thread/currentThread)))]
    (.addAppender (LogManager/getLogger "com.sun.xml.internal.ws.transport.http.client") appender)
    (.addAppender (LogManager/getLogger "org.apache.http") appender)
    (try (f)
         (finally
           (swap! *logs* #(-> (->> %
                                   (remove nil?)
                                   (str/join "\n"))
                              (str/replace #"(?i)key=[^ ]+" "key=[REDACTED]")
                              (str/replace #"(\"token\"|\"TOKEN\"|\"access_token\")\s*:\s*\".*\"," (fn [[_ k]] (str k " : \"[REDACTED]\",")))
                              (str/replace #"(?i)\"Authorization: Basic .*\"" "\"Authorization: Basic [REDACTED]\"")
                              (str/replace #"(?i)(\"pPhotoPlate\"|\"pPhoto\"|\"pPhotoAdditional\") : \".*\"," (fn [[_ k]] (str k " : \"[REDACTED]\",")))
                              (str/replace #"(?i)\"Authorization: Bearer .*\"" "\"Authorization: Bearer [REDACTED]\"")
                              (str/replace #"(?i)\"(access_token|expires_in)\" *?: *?(\".*?\"|[0-9]+)" "\"$1\":\"[REDACTED]\"")
                              (str/replace #"(?i)\"username *?= *?.*&?password *?= *?.*?&grant_type *?= *?.*?\"" "\"username=[REDACTED]&password=[REDACTED]&grant_type=[REDACTED]\"")
                              (str/replace (-> env :uzcard :username) "[REDACTED]")
                              (str/replace (-> env :uzcard :password) "[REDACTED]")))
           (.removeAppender (LogManager/getLogger "com.sun.xml.internal.ws.transport.http.client") appender)
           (.removeAppender (LogManager/getLogger "org.apache.http") appender)))))
