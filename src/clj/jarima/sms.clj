(ns jarima.sms
  (:refer-clojure :exclude [send])
  (:import [java.net Socket])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [jarima.config :refer [env]]
            [clojure.tools.logging :as log]))


(def cyr->lat
  (zipmap
    ["№", "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я", "ў", "қ", "ғ", "ҳ"
          "А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я", "Ў", "Қ", "Ғ", "Ҳ"]
    ["#", "a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "h", "ts", "ch", "sh", "sch", "", "i", "", "e", "ju", "ja", "o'", "q", "g'", "h"
          "A", "B", "V", "G", "D", "E", "E", "Zh", "Z", "I", "Y", "K", "L", "M", "N", "O", "P", "R", "S", "T", "U", "F", "H", "Ts", "Ch", "Sh", "Sch", "", "I", "", "E", "Ju", "Ja", "O'", "Q", "G'", "H"]))


(defn fit-text
  [s]
  (-> (str/join #"" (map (some-fn cyr->lat identity) (str/split s #"")))
      (str/replace #"\s+" " ")
      (str/trim)))


(comment
  (fit-text "hello \n №123 привет йўқ"))


(defn send
  [phone text]
  (when (and (get-in env [:sms :host]) (get-in env [:sms :port]))
    (try
      (let [text (fit-text text)]
        (log/info "Sending to" phone " " text)
        (with-open [socket (Socket. (get-in env [:sms :host]) (get-in env [:sms :port]))
                    writer (io/writer (.getOutputStream socket))
                    reader (io/reader (.getInputStream socket))]
          (.write writer (format "3%s@%s\n" phone text))
          (.flush writer)
          (= "ok" (.readLine reader))))
      (catch Throwable e
        (log/error e)))))
