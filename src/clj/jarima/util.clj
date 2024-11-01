(ns jarima.util
  (:import [java.net URI]
           [javax.imageio ImageIO]
           [java.util Locale UUID]
           [java.util.regex Pattern]
           [java.awt.image BufferedImage]
           [java.io ByteArrayOutputStream]
           [java.time.format DateTimeFormatter]
           [java.time LocalDateTime LocalDate LocalTime ZoneOffset]
           [org.apache.http.client.utils URIBuilder])
  (:require [reitit.core :as r]
            [jarima.db.core :as db]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [medley.core :refer [map-vals map-keys]]
            [buddy.core.codecs.base64 :as base64]
            [meta-merge.core :refer [meta-merge]]
            [image-resizer.util :as resizer.util]
            [image-resizer.core :as resizer.core]
            [clojure.core.async :refer [<! chan go-loop pipeline-blocking sliding-buffer close!]]
            [reitit.ring :as ring]))


(defn ceil [x]
  (int (Math/ceil x)))


(defn floor [x]
  (int (Math/floor x)))


(defn guid->uuid [data]
  (try
    (UUID/fromString
      (clojure.string/replace data
                              #"(\w{8})(\w{4})(\w{4})(\w{4})(\w{12})"
                              "$1-$2-$3-$4-$5"))
    (catch Exception _
      nil)))


(defn uuid->guid [id]
  (apply str (str/split (str id) #"-")))


(defn available-processors
  []
  (.availableProcessors (Runtime/getRuntime)))


(defn now
  []
  (LocalDateTime/now))

(defn today
  []
  (LocalDate/now))


(defmacro cond-let
  [& forms]
  {:pre [(even? (count forms))]}
  (when forms
    (let [[test-exp result-exp & more-forms] forms]
      (if (= :let test-exp)
        `(let ~result-exp
           (cond-let ~@more-forms))
        `(if ~test-exp
           ~result-exp
           (cond-let ~@more-forms))))))


(def uzcard-local-date-time-format (DateTimeFormatter/ofPattern "yyyyMMdd"))


(defn unparse-uzcard-local-date-time
  [local-date-time]
  (.format local-date-time uzcard-local-date-time-format))


(def local-date-time-precise-pattern
  DateTimeFormatter/ISO_LOCAL_DATE_TIME)


(defn parse-local-date-time-precise [s]
  (LocalDateTime/parse s local-date-time-precise-pattern))


(defn unparse-local-date-time-precise [local-date-time]
  (when local-date-time
    (.format local-date-time local-date-time-precise-pattern)))


(def local-date-time-pattern
  (-> (DateTimeFormatter/ofPattern "d MMMM yyyy года, HH:mm")
      (.withLocale (Locale. "ru" "RU"))))


(defn parse-local-date-time [s]
  (LocalDateTime/parse s local-date-time-pattern))


(defn unparse-local-date-time [local-date-time]
  (when local-date-time
    (.format local-date-time local-date-time-pattern)))


(def local-date-time-short-pattern
  (-> (DateTimeFormatter/ofPattern "dd.MM.yyyy H:mm")))


(defn parse-local-date-time-short [s]
  (LocalDateTime/parse s local-date-time-short-pattern))


(defn unparse-local-date-time-short [local-date-time]
  (when local-date-time
    (.format local-date-time local-date-time-short-pattern)))


(def local-date-pattern
  (-> (DateTimeFormatter/ofPattern "d MMMM yyyy года")
      (.withLocale (Locale. "ru" "RU"))))


(defn parse-local-date [s]
  (LocalDateTime/parse s local-date-pattern))


(defn unparse-local-date [local-date-time]
  (when local-date-time
    (.format local-date-time local-date-pattern)))


(def local-date-short-pattern
  (-> (DateTimeFormatter/ofPattern "dd.MM.yyyy")))


(defn parse-local-date-short [s]
  (LocalDate/parse s local-date-short-pattern))


(defn unparse-local-date-short [local-date-time]
  (when local-date-time
    (.format local-date-time local-date-short-pattern)))


(def local-time-short-pattern
  (-> (DateTimeFormatter/ofPattern "H:mm")))


(defn parse-local-time-short [s]
  (LocalTime/parse s local-time-short-pattern))


(defn unparse-local-time-short [local-time]
  (.format local-time local-time-short-pattern))


(def local-time-pattern
  (-> (DateTimeFormatter/ofPattern "HH:mm:ss")))


(defn parse-local-time [s]
  (LocalTime/parse s local-time-pattern))


(defn unparse-local-time [local-time]
  (.format local-time local-time-pattern))


(def local-date-long-pattern
  (-> (DateTimeFormatter/ofPattern "EEEE, d MMMM yyyy года")
      (.withLocale (Locale. "ru" "RU"))))


(defn parse-local-date-long [s]
  (LocalDate/parse s local-date-long-pattern))


(defn unparse-local-date-long [local-date-time]
  (when local-date-time
    (.format local-date-time local-date-long-pattern)))


(defn local-date-time
  [local-date local-time]
  (LocalDateTime/of local-date local-time))


(defn route-path
  ([request name]
   (route-path request name nil nil))
  ([request name path-params]
   (route-path request name path-params nil))
  ([request name path-params query-params]
   (-> (::r/router request)
       (r/match-by-name! name path-params)
       (r/match->path (not-empty query-params)))))


(defn handler-name->route-path
  ([handler name]
   (handler-name->route-path handler name nil nil))
  ([handler name path-params]
   (handler-name->route-path handler name path-params nil))
  ([handler name path-params query-params]
   (-> handler
       (ring/get-router)
       (reitit.core/match-by-name! name path-params)
       (reitit.core/match->path (not-empty query-params)))))


(defn route-matched?
  [request name]
  (let [match (::r/match request)]
    (= name (:name (:data match)))))


(defmacro mute
  [& body]
  `(try
     ~@body
     (catch Throwable _# nil)))


(defn vectorify-vals
  ([m]
   (vectorify-vals m (constantly true)))
  ([m vectorify?]
   (reduce
     (fn [a [k v]]
       (if (some? v)
         (assoc a
           k (if (vectorify? k) [v] v))
         a))
     {} m)))


(defn flatten-key
  ([m]
   (flatten-key m []))
  ([m p]
   (reduce
     (fn [acc [k v]]
       (if (map? v)
         (into acc (flatten-key v (conj p k)))
         (conj acc [(conj p k) v])))
     []
     m)))


(defn to-query-string [m]
  (http/generate-query-string
    (->> (flatten-key m)
         (map (fn [[k v]] [(map name k) v]))
         (map (fn [[[f & r] v]] [(apply str f (map #(str "[" % "]") r)) v]))
         (into {}))
    nil
    :array))


(defn paged-query
  [make-sql-map page & columns]
  (let [page-size 20
        stats (-> {:select (conj columns :%count.*)
                   :from   [[(-> (make-sql-map nil)
                                 (dissoc :order-by))
                             :target]]}
                  (db/query)
                  (first))
        pages (-> (:count stats) (/ page-size) (ceil) (int))
        page (-> page (or 1) (min pages) (max 1))]
    (-> stats
        (assoc :current-page page)
        (assoc :total-pages pages)
        (assoc :total-count (:count stats))
        (assoc :paged-rows (-> {:page page :size page-size}
                               (make-sql-map)
                               (db/query))))))

(defn paged-api-query
  [make-sql-map page-size page row-fn & columns]
  (let [page-size page-size
        stats (-> {:select (conj columns :%count.*)
                   :from   [[(-> (make-sql-map nil)
                                 (dissoc :order-by))
                             :target]]}
                  (db/query)
                  (first))
        pages (-> (:count stats) (/ page-size) (ceil) (int))
        page (-> page (or 1) (min pages) (max 1))]
    (-> stats
        (assoc :current_page page)
        (assoc :total_pages pages)
        (assoc :total_count (:count stats))
        (assoc :page_size page-size)
        (assoc :results (-> {:page page :size page-size}
                            (make-sql-map)
                            (db/query row-fn))))))



(defn request-locale
  "Tries to guess client preferred locale from accept-language, cookie, query-param"
  [{:keys [cookies query-params accept]} available-locales]
  (->> [(-> "locale" query-params keyword)
        (-> "locale" cookies :value keyword)
        (-> "language" accept)
        (-> available-locales ffirst)]
       (filter available-locales)
       (first)))


(def available-locales
  (memoize
    (fn
      ([translations]
       (available-locales translations nil))
      ([translations preferred-locale]
       (let [fallback (-> preferred-locale translations meta :fallback)
             score #(/ (or (-> % translations meta :priority) 0)
                       (if (= % fallback) 10 1)
                       (if (= % preferred-locale) 100 1))]
         (into (sorted-map-by #(< (score %1) (score %2)))
               (map-vals (comp :name meta) translations)))))))


(defn sink
  ([]
   (sink nil))
  ([f]
   (let [ch (chan)]
     (go-loop []
       (when-let [x (<! ch)]
         (when f (f x))
         (recur)))
     ch)))

(defn parse-int [i]
  (Integer/parseInt i))


(defn parse-double [d]
  (Double/parseDouble d))


(defn round [d]
  (Math/round d))


(defn floor [d]
  (long (Math/floor d)))



(defn local-date?
  [x]
  (instance? LocalDate x))


(defn local-time?
  [x]
  (instance? LocalTime x))


(defn local-date-time?
  [x]
  (instance? LocalDateTime x))


(defn format-phone
  [s]
  (when (some? s)
    (if-let [matches (re-matches #"(998)(\d{2})(\d{3})(\d{2})(\d{2})" s)]
      (->> (rest matches)
           (str/join " ")
           (str "+"))
      s)))


(defn format-card
  [s]
  (when (some? s)
    s))


(defn bytes->base64 [bytes]
  (buddy.core.codecs/bytes->str
    (base64/encode bytes)))


(defn buffered-image->bytes
  [^BufferedImage image format]
  (with-open [stream (ByteArrayOutputStream.)]
    (ImageIO/write image format stream)
    (.toByteArray stream)))


(defn image->base64 [image max-size]
  (let [image (resizer.util/buffered-image image)
        width (first (resizer.core/dimensions image))
        min-step (/ 1 width)]
    (loop [k 1
           step 1/2
           min-size max-size]
      (let [base64 (-> (resizer.core/resize-to-width image (* k width))
                       (buffered-image->bytes "jpg")
                       (bytes->base64))
            size (double (/ (count base64) 1024))]
        (cond
          (> size max-size)
          (recur (- k step)
                 (max min-step (/ step 2))
                 (if (< (/ step 2) min-step)
                   (dec min-size)
                   min-size))

          (and (< size min-size) (< k 1))
          (recur (+ k step)
                 (max (/ 1 width) (/ step 2))
                 min-size)

          :else base64)))))


(defn pretty-errors
  ([errors]
   (pretty-errors errors ";\n"))
  ([errors del]
   (pretty-errors errors del ""))
  ([errors del context]
   (reduce-kv
     (fn [acc k v]
       (if (map? v)
         (str acc (when acc del) (pretty-errors v del (str context " " (str/join "." (map #(some-> % name) (if (coll? k) k [k]))))))
         (str acc (when acc del) context " " (str/join "." (map #(some-> % name) (if (coll? k) k [k]))) ": " (str/join ", " v))))
     nil errors)))


(defn translate-nested-errors
  ([errors t-fn]
   (reduce-kv
     (fn [acc k v]
       (assoc acc k (if (map? v)
                      (translate-nested-errors v t-fn)
                      (vec (map t-fn v)))))
     {} errors)))


(defn to-date-range
  [s]
  (let [[gte lte] (str/split s #"-")]
    {:gte (parse-local-date-short gte)
     :lte (parse-local-date-short lte)}))


(defn to-open-right-date-range [{:keys [gte lte]}]
  (when (and (some? gte) (some? lte))
    {:gte gte
     :lt  (.plusDays lte 1)}))


(defn invert-open-date-range [{:keys [gte lt]}]
  (when (and (some? gte) (some? lt))
    {:gte lt
     :lt  gte}))


(defn unparse-open-right-date-range [{:keys [^LocalDate gte ^LocalDate lt]}]
  (->> [(-> gte unparse-local-date-short)
        (-> lt (.minusDays 1) unparse-local-date-short)]
       (str/join "-")))


(defn lines [reader]
  (map #(hash-map :line %1 :text %2)
       (iterate inc 1)
       (reverse (line-seq reader))))


(defn read-log
  [file size from]
  (with-open [reader (io/reader file)]
    (doall
      (if (some? from)
        (->> (lines reader)
             (drop-while #(<= (:line %) from))
             (take size))))))

(defn host
  [url]
  (.getHost (URI. url)))


(defn validate-referrer
  [request base-url]
  (let [referer (get-in request [:headers "referer"])]
    (when (and referer (.startsWith referer base-url))
      (subs referer (count base-url)))))


(defn remember-referer
  [response request base-url]
  (if-let [referer (validate-referrer request base-url)]
    (assoc response :session (assoc (:session request) :referer referer))
    response))


(defn recall-referer
  [response request base-url]
  (if-let [referer (or (get-in request [:session :referer])
                       (validate-referrer request base-url))]
    (-> response
        (assoc-in [:headers "Location"] referer)
        (assoc :session (dissoc (:session request) :referer)))
    response))

(defn ->java-local-date-time
  [^org.joda.time.LocalDateTime local-date-time]
  (java.time.LocalDateTime/of
    (.getYear local-date-time)
    (.getMonthOfYear local-date-time)
    (.getDayOfMonth local-date-time)
    (.getHourOfDay local-date-time)
    (.getMinuteOfHour local-date-time)
    (.getSecondOfMinute local-date-time)
    0))


(defn valid-regex
  [regex]
  (try
    (Pattern/compile regex) true
    (catch Throwable _ false)))


(defmethod print-method LocalDateTime [this ^java.io.Writer w]
  (.write w "#local-date-time ")
  (print-method (unparse-local-date-time-precise this) w))


(defmacro graceful-loop
  [interval-ms & body]
  `(let [s# (promise)
         f# (future
              (loop []
                ~@body
                (when (= ::timeout (deref s# ~interval-ms ::timeout))
                  (recur))))]
     (fn []
       (log/debug "Stopping gracefully...")
       (deliver s# ::stop)
       (try
         (deref f#)
         (catch Throwable e# (log/error e#))))))


(defn add-uri-query [uri m]
  (let [uri-builder (URIBuilder. ^String uri)]
    (doseq [[k v] m]
      (.addParameter uri-builder
                     ^String (name k)
                     (str v)))
    (.toString (.build uri-builder))))


(defn remove-uri-query [uri]
  (let [uri-builder (URIBuilder. ^String uri)]
    (.removeQuery uri-builder)
    (.toString (.build uri-builder))))


(defn split-uri-query [uri]
  (let [uri-builder (URIBuilder. ^String uri)
        query (reduce (fn [acc v] (assoc acc
                                    (keyword (.getName v)) (.getValue v)))
                      {} (.getQueryParams uri-builder))]
    (.removeQuery uri-builder)
    [(.toString (.build uri-builder)) query]))


(defn relative-path [uri]
  (let [uri-builder (URIBuilder. ^String uri)]
    (.getPath uri-builder)))


(defn uri-equal? [uri1 uri2]
  (let [uri-builder1 (URIBuilder. ^String uri1)
        uri-builder2 (URIBuilder. ^String uri1)]
    (= (.build (.removeQuery uri-builder1))
       (.build (.removeQuery uri-builder2)))))


(defn secondsBetween [dt1 dt2]
  (- (.toEpochSecond dt1 (ZoneOffset/ofHours +5))
     (.toEpochSecond dt2 (ZoneOffset/ofHours +5))))


(defn ->error-response [errors]
  {:error             (name (first (ffirst errors)))
   :error_description (pretty-errors errors ", ")
   :errors            (map-keys #(if (vector? %) (last %) %) errors)})


(defn nest-errors [errors]
  (reduce (fn [acc [ks v]]
            (if (vector? ks)
              (assoc-in acc ks v)
              (assoc acc ks v))) {} errors))


(defn ->api-errors [validation-errors t-fn]
  (let [errors (-> validation-errors
                   (nest-errors)
                   (translate-nested-errors t-fn))]
    {:error             "invalid_request"
     :error_description (pretty-errors errors)
     :errors            errors}))


;; Kiril to latin conversion
(def lat2cyrmap {:A  "А", :a "а", :B "Б", :b "б", :C "Ц", :c "ц", :Ch "Ч", :ch "ч", :Ç "Ч", :ç "ч", :Ć "Ц", :ć "ц", :D "Д", :d "д", :Đ "Д", :đ "д",
                 :E  "Е", :e "е", :F "Ф", :f "ф", :G "Г", :g "г", :Ǵ "Г", :ǵ "г", :G' "Г", :g' "г", :H "Х", :h "х",
                 :I  "И", :i "и", :J "Ж", :j "ж", :K "К", :k "к", :L "Л", :l "л", :M "М", :m "м", :N "Н", :n "н", :O "О", :o "о", :Ó "О", :ó "о", :O' "О", :o' "о",
                 :P  "П", :p "п", :R "Р", :r "р", :S "С", :s "с", :Sh "Ш", :sh "ш", :Ş "Ш", :ş "ш"
                 :T  "Т", :t "т", :U "У", :u "у", :V "В", :v "в", :Z "З", :z "з", :Ž "З", :ž "з"
                 :Yo "Ё" :yo "ё" :Ye "е" :ye "е"})


; Cyrillic to Latin map
(def cyr2latmap {:А "A", :а "a", :Б "B", :б "b", :В "V", :в "v", :Г "G", :г "g", :Д "D", :д "d", :Е "E", :е "e", :Ё "Yo", :ё "yo"
                 :Ж "J", :ж "j", :З "Z", :з "z", :И "I" :и "i" :К "K", :к "k", :Л "L", :л "l"
                 :М "M", :м "m", :Н "N", :н "n", :О "O", :о "o", :П "P", :п "p", :Р "R", :р "r", :С "S", :с "s",
                 :Т "T", :т "t", :У "U", :у "u", :Ф "F", :ф "f", :Х "H", :х "h", :Ц "C", :ц "c", :Ч "Ch", :ч "ch",
                 :Ш "Sh", :ш "sh", :Щ "Sh", :щ "sh" :Дж "J" :дж "j"})


(defn- make-str-from-chars
  "make a string from a sequence of  characters"
  ([chars] (make-str-from-chars chars ""))
  ([chars result]
   (if-let [chars (seq chars)]
     (recur (next chars) (str result (first chars)))
     result)))


(defn- make-chars-from-string
  "make a sequence of characters from a string"
  ([string] (make-chars-from-string string (vector)))
  ([string result]
   (if-let [string (seq string)]
     (cond
       (= "C" (str (first string))) (cond (= "h" (str (second string))) (recur (next (next string)) (conj result (str (first string) "h")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "c" (str (first string))) (cond (= "h" (str (second string))) (recur (next (next string)) (conj result (str (first string) "h")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "S" (str (first string))) (cond (= "h" (str (second string))) (recur (next (next string)) (conj result (str (first string) "h")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "s" (str (first string))) (cond (= "h" (str (second string))) (recur (next (next string)) (conj result (str (first string) "h")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "O" (str (first string))) (cond (= "'" (str (second string))) (recur (next (next string)) (conj result (str (first string) "'")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "o" (str (first string))) (cond (= "'" (str (second string))) (recur (next (next string)) (conj result (str (first string) "'")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "G" (str (first string))) (cond (= "'" (str (second string))) (recur (next (next string)) (conj result (str (first string) "'")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "g" (str (first string))) (cond (= "'" (str (second string))) (recur (next (next string)) (conj result (str (first string) "'")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "Y" (str (first string))) (cond (= "o" (str (second string))) (recur (next (next string)) (conj result (str (first string) "o")))
                                          (= "e" (str (second string))) (recur (next (next string)) (conj result (str (first string) "e")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "y" (str (first string))) (cond (= "o" (str (second string))) (recur (next (next string)) (conj result (str (first string) "o")))
                                          (= "e" (str (second string))) (recur (next (next string)) (conj result (str (first string) "e")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "Д" (str (first string))) (cond (= "ж" (str (second string))) (recur (next (next string)) (conj result (str (first string) "ж")))
                                          :else (recur (next string) (conj result (str (first string)))))
       (= "д" (str (first string))) (cond (= "ж" (str (second string))) (recur (next (next string)) (conj result (str (first string) "ж")))
                                          :else (recur (next string) (conj result (str (first string)))))
       :else (recur (next string) (conj result (str (first string))))) result)))


(defn- convert-to-cyr-alg
  "converts latin sequnece of characters to cyrillic"
  ([latintext] (convert-to-cyr-alg latintext (vector)))
  ([latintext result]
   (if (not (empty? latintext))
     (cond
       (= nil (lat2cyrmap (keyword (first latintext)))) (recur (rest latintext) (conj result (first latintext)))
       :else (recur (rest latintext) (conj result (lat2cyrmap (keyword (first latintext)))))) result)))

(defn- convert-to-lat-alg
  "converts cyrillic sequnece of characters to latin"
  ([cyrillictext] (convert-to-lat-alg cyrillictext (vector)))
  ([cyrillictext result]
   (if (not (empty? cyrillictext))
     (cond
       (= nil (cyr2latmap (keyword (first cyrillictext)))) (recur (rest cyrillictext) (conj result (first cyrillictext)))
       :else (recur (rest cyrillictext) (conj result (cyr2latmap (keyword (first cyrillictext)))))) result)))

(defn convert-to-cyr
  "converts latin text to cyrillic"
  [latintext]
  (make-str-from-chars (convert-to-cyr-alg (make-chars-from-string latintext))))


(defn convert-to-lat
  "converts cyrillic text to latin"
  [cyrillictext]
  (make-str-from-chars (convert-to-lat-alg (make-chars-from-string cyrillictext))))

(defn replace-ext
  ([file-name new-ext]
   (replace-ext file-name nil new-ext))
  ([file-name suffix new-ext]
   (str/join "." (concat (butlast (str/split file-name #"\."))
                         (if suffix
                           [suffix new-ext]
                           [new-ext])))))
