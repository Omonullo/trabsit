(ns jarima.test.translations
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [expectations.clojure.test :refer [expect]]
            [jarima.config :refer [dictionary env]]))



;; Use this to find missing translations
(def missing-translations (atom {:uz_la #{}
                                 :uz_cy #{}}))

#_(println (str/join "\n" (set/union (:uz_la @missing-translations) (:uz_cy @missing-translations))))


;; Uncomment when all translations are verified
#_(deftest test-dictionary
    (reset! missing-translations {:uz_la #{}
                                  :uz_cy #{}})
    (testing "validation translations"
      (let [validation (slurp (io/file "src/clj/jarima/validation.clj"))
            validation-messages (->> validation
                                     (re-seq #"(\:message|\:blank-message)\s*\"(.*?)\"")
                                     (map #(get % 2))
                                     (set))
            missing-trs {:uz_la (set/difference validation-messages (set (keys (:uz_la dictionary))))
                         :uz_cy (set/difference validation-messages (set (keys (:uz_cy dictionary))))}]
        (swap! missing-translations #(merge-with set/union % missing-trs))

        (expect nil (seq (:uz_la missing-trs))
                "Expect no missing uz_la translations")
        (expect nil (seq (:uz_cy missing-trs))
                "Expect no missing uz_cy translations")))



    (testing "validation html translations"
      (let [html-files (filter #(and (.isFile %)
                                     (re-matches #".*\.html" (.toString (.getFileName (.toPath %)))))
                               (file-seq (io/file "resources/html/"))) (user/mig)
            missing-trs (->> html-files
                             (map #(let [messages (->> %
                                                       (slurp)
                                                       (re-seq #"\{[{|\s]\"(.*?)\"\|")
                                                       (map second)
                                                       (set))]
                                     {:uz_la (set/difference messages (set (keys (-> dictionary :translations :uz_la))))
                                      :uz_cy (set/difference messages (set (keys (-> dictionary :translations :uz_cy))))}))
                             (apply merge-with set/union))]
        (swap! missing-translations #(merge-with set/union % missing-trs))
        (expect nil (seq (:uz_la missing-trs))
                "Expect no missing uz_la translations")
        (expect nil (seq (:uz_cy missing-trs))
                "Expect no missing uz_cy translations")))


    (testing "validation vue translations"
      (let [html-files (filter #(and (.isFile %)
                                     (re-matches #".*\.vue" (.toString (.getFileName (.toPath %)))))
                               (file-seq (io/file "resources/app/vue/")))
            missing-trs (->> html-files
                             (map #(let [messages (->> %
                                                       (slurp)
                                                       (re-seq #"\{[{|\s]\"(.*?)\"\|")
                                                       (map second)
                                                       (set))]
                                     {:uz_la (set/difference messages (set (keys (-> dictionary :translations :uz_la))))
                                      :uz_cy (set/difference messages (set (keys (-> dictionary :translations :uz_cy))))}))
                             (apply merge-with set/union))]
        (expect nil (seq (:uz_la missing-trs))
                "Expect no missing uz_la translations")
        (expect nil (seq (:uz_cy missing-trs))
                "Expect no missing uz_cy translations"))))
