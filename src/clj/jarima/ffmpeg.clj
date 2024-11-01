(ns jarima.ffmpeg
  (:require [jarima.util :as util]
            [cheshire.core :as json]
            [medley.core :refer [index-by]]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :as log])
  (:import [java.io File]))


(defn extract-thumbnail
  [^File video]
  (let [result (sh "ffmpeg"
                   "-i" (.getAbsolutePath video)
                   "-vframes" "1"
                   "-vf" "scale='min(320,iw)':-1"
                   "-q:v" "2"
                   "D:\\output.mp4" :out-enc :bytes)]
    (if (zero? (:exit result))
      (:out result)
      (throw (ex-info "Extracting thumbnail failed." result)))))


(defn extract-thumbnail-from-url [url]
  (let [result (sh "ffmpeg"
                   "-i" url
                   "-vframes" "1"
                   "-vf" "scale='min(320,iw)':-1"
                   "-q:v" "2"
                   "-" :out-enc :bytes)]
    (if (zero? (:exit result))
      (:out result)
      (throw (ex-info "Extracting thumbnail failed." result)))))


(defn probe*
  [^File video]
  (let [result (sh "ffprobe"
                   "-v" "quiet"
                   "-print_format" "json"
                   "-show_error"
                   "-show_format"
                   "-show_streams"
                   (.getAbsolutePath video))]
    (if (zero? (:exit result))
      (json/parse-string (:out result) keyword)
      (do
        (log/error
          (str "Failed probing output:" (json/encode result)))
        (throw
          (ex-info "Probing failed."
                   (update result :out
                           #(try (json/parse-string % keyword)
                                 (catch Throwable _ %)))))))))


(defn probe
  "Return map containing probing information of a video:
  {:width 640, :height 480, :duration 348, :video-codec \"h264\", :audio-codec \"aac\"}"
  [^File video]
  (let [{{:keys [width height duration] video-codec :codec_name} :video
         {audio-codec :codec_name} :audio}
        (index-by (comp keyword :codec_type) (:streams (probe* video)))]
    {:width       width
     :height      height
     :duration    (some-> duration util/parse-double util/floor)
     :video-codec video-codec
     :audio-codec audio-codec}))
