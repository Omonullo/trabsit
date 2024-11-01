(defproject jarima "0.1.0-SNAPSHOT"

  :dependencies [[buddy "2.0.0"]
                 [faker "0.2.2"]
                 [nrepl "0.6.0"]
                 [conman "0.8.3"]
                 [cprop "0.1.15"]
                 [hiccup "1.0.5"]
                 [medley "1.2.0"]
                 [mount "0.1.16"]
                 [duratom "0.4.3"]
                 [cheshire "5.8.1"]
                 [honeysql "0.9.4"]
                 [selmer "1.12.11"]
                 [clj-http "3.10.0"]
                 [compojure "1.5.1"]
                 [raven-clj "1.6.0"]
                 [markdown-clj "1.0.7"]
                 [jarohen/chime "0.2.2"]
                 [image-resizer "0.1.10"]
                 [io.minio/minio "6.0.3"]
                 [metosin/reitit "0.3.7"]
                 [ring/ring-core "1.7.1"]
                 [image-resizer "0.1.10"]
                 [buddy/buddy-auth "2.1.0"]
                 [luminus-immutant "0.2.5"]
                 [metosin/muuntaja "0.6.3"]
                 [camel-snake-kebab "0.4.2"]
                 [clojure.java-time "0.3.2"]
                 [dk.ative/docjure "1.12.0"]
                 [prismatic/schema "1.1.10"]
                 [luminus-migrations "0.6.4"]
                 [ring/ring-defaults "0.3.2"]
                 [metosin/spec-tools "0.9.2"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [com.taoensso/carmine "2.19.1"]
                 [ring-middleware-accept "2.0.3"]
                 [org.clojure/core.async "0.4.490"]
                 [expectations/clojure-test "1.2.1"]
                 [nilenso/honeysql-postgres "0.2.4"]
                 [com.novemberain/validateur "2.6.0"]
                 [metosin/ring-http-response "0.9.1"]
                 [ring-cors "0.1.13"]
                 [org.postgresql/postgresql "42.2.10"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [org.clojure/tools.logging "0.5.0-alpha.1"]
                 [org.apache.logging.log4j/log4j-jul "2.11.1"]
                 [org.apache.logging.log4j/log4j-api "2.11.0"]
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-1.2-api "2.11.0"]]

  :min-lein-version "2.0.0"
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :resource-paths ["target/sass" "target/babel" "target/rollup" "resources" "target/react"]
  :target-path "target/%s/"
  :main ^:skip-aot jarima.core
  :plugins [[lein-immutant "2.1.0"]
            [lein-shell "0.5.0"]]
  :clean-targets ^{:protect false} [:target-path]
  :shell {:commands {"npm" {:windows "C:\\Program Files\\nodejs\\npm.cmd"}}}
  :profiles {:uberjar       {:omit-source    true
                             :prep-tasks     [["shell" "npm" "run" "build"]
                                              ["shell" "npm" "run" "build-offense-map"]
                                              ["shell" "npm" "run" "build-report-review"]
                                              ["compile"]]
                             :aot            :all
                             :uberjar-name   "jarima.jar"
                             :source-paths   ["env/prod/clj"]
                             :resource-paths ["env/prod/resources" "target/npm"]}

             :dev           [:project/dev :profiles/dev]
             :test          [:project/dev :project/test :profiles/test]

             :project/dev   {:jvm-opts       ["-Dconf=dev-config.edn" "-Duser.timezone=Asia/Tashkent" "-XX:-OmitStackTraceInFastThrow" "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"]
                             :source-paths   ["env/dev/clj"]
                             :resource-paths ["env/dev/resources"]
                             :repl-options   {:init-ns user}
                             :injections     [(require 'pjstadig.humane-test-output)
                                              (pjstadig.humane-test-output/activate!)]
                             :plugins        [[com.jakemccrary/lein-test-refresh "0.23.0"]]
                             :dependencies   [[expound "0.7.2"]
                                              [pjstadig/humane-test-output "0.9.0"]
                                              [prone "1.6.1"]
                                              [ring/ring-devel "1.7.1"]
                                              [ring/ring-mock "0.3.2"]]}
             :project/test  {:jvm-opts       ["-Dconf=test-config.edn"]
                             :resource-paths ["env/test/resources"]}

             :profiles/dev  {}
             :profiles/test {}})
