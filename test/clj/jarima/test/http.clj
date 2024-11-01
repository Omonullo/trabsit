(ns jarima.test.http
  (:require
    [muuntaja.core :as m]
    [mount.core :as mount]
    [jarima.util :as util]
    [cheshire.core :as json]
    [clojure.string :as str]
    [clj-http.client :as chc]
    [jarima.http :refer :all]
    [clj-http.core :as chcore]
    [clojure.test :refer :all]
    [jarima.config :refer [env]]
    [clojure.java.jdbc :as jdbc]
    [ring.mock.request :refer []]
    [clj-http.cookies :as chcookie]
    [expectations.clojure.test :refer [expect]]
    [ring.middleware.reload :refer [wrap-reload]]
    [ring.util.http-response :refer [ok bad-request bad-request!]]
    [medley.core :refer [update-existing map-keys assoc-some random-uuid uuid map-kv]]
    [jarima.db.query :as q]
    [jarima.db.core :as db]))


(defn expect-some [val msg]
  (expect some? val msg))

(defmacro with-fake-db [& body]
  `(jdbc/with-db-transaction [t-conn# jarima.db.core/*db*]
     (jdbc/db-set-rollback-only! t-conn#)
     (with-redefs [jarima.db.core/*db* t-conn#]
       ~@body)))


(defn parse-json [body]
  (m/decode (m/create) "application/json" body))


(defn unparse-json [body]
  (m/encode (m/create) "application/json" body))


(defn request [name method & {:keys [path form params headers multipart body]}]
  (-> (chc/request {:method                     method
                    :url                        (->> params
                                                     (util/handler-name->route-path app name path)
                                                     (str (:test-url env)))
                    :throw-exceptions           false
                    :form-params                form
                    :multipart                  multipart
                    :body                       body
                    :accept                     :json
                    :headers                    headers
                    :flatten-nested-form-params true})
      (update :body parse-json)))


(use-fixtures
  :once
  (fn [f]
    (mount/start #'jarima.config/env
                 #'jarima.http/app
                 #'jarima.db.core/*db*
                 #'jarima.http/server
                 #'jarima.redis/*redis*
                 #'jarima.minio/client
                 #'jarima.minio/public-client
                 #'jarima.config/dictionary)
    (f)))


(def dynamic-param "dynamic")
(def static-param "static")


(def oauth-code-client {:enabled            "true"
                        :redirect_uri       (str (util/add-uri-query
                                                   "http://default_uri" {:static-param static-param}) ", http://whatever/path")
                        :default_scope      "send-report"
                        :allowed_scope      "send-report,read-card-phone"
                        :error_redirect_uri (util/add-uri-query "http://error.com" {:static-param static-param})
                        :id                 (str (random-uuid))
                        :name               "CODE_CLIENT"
                        :grant_type         "code"
                        :secret             (util/uuid->guid (random-uuid))
                        :url                "http://localhost"
                        :logo               "http://localhost"})


(def jarima-report {:incident_date    (util/unparse-local-date-short (.minusDays (util/today) 2))
                    :incident_time    "00:13",
                    :address          "ADDRESS sample",
                    :video_id         "TO BE ADDED",
                    :with_extra_video true,
                    :extra_video_type "rear"
                    :extra_video_id   "TO BE ADDED"
                    :district_id      "b464be85-d850-428f-86b8-3e752a3912dc",
                    :area_id          "811a6aa2-2446-4abc-b91a-7c8b61310721",
                    :offenses         {:WHATEVER {:vehicle_id "D 123123"
                                                  :testimony  "TESTIMONY SAMPLE"}},
                    :reward_params    {:card "8600 0000 0000 0000"},
                    :lat              41.283463,
                    :lng              69.339789})



(def approvement {:scope         "read-card-phone"
                  :state         "123"
                  :client_id     (:id oauth-code-client)
                  :response_type "code"
                  :id            (str (random-uuid))
                  :redirect_uri  (->> {:dynamic-param dynamic-param}
                                      (util/add-uri-query
                                        (second (map str/trim (str/split (:redirect_uri oauth-code-client) #",")))))})

(def phone "+998900000000")


(def citizen {:id           (str (random-uuid))
              :first_name   "first name sample"
              :middle_name  "middle name sample"
              :last_name    "last name sample"
              :email        "example@mail.com sample"
              :phone        phone
              :second_phone "+998 92 152 12 15"
              :secret       (util/uuid->guid (random-uuid))
              :area_id      "811a6aa2-2446-4abc-b91a-7c8b61310721"
              :district_id  "b464be85-d850-428f-86b8-3e752a3912dc"
              :address      "address sample"
              :zipcode      "123456"
              :password     "1235678"})



(def client-credentials {:client_id     (:id oauth-code-client)
                         :client_secret (:secret oauth-code-client)
                         :grant_type    "code"})



(defn misc-send-and-verify-code [phone]
  (testing "misc/send-code"
    (testing "> error-response > wrong phone"
      (let [result (request :misc/send-code :post :form {:phone "WRONG"})]
        (expect (get-in result [:body :errors :phone]))
        (expect 400 (:status result) "Expect HTTP 400")))
    (let [result (request :misc/send-code :post :form {:phone phone})
          code (get-in result [:body :code])]
      (expect nil (get-in result [:body :errors]))
      (expect (get-in result [:body :ttl]))
      (expect 200 (:status result))
      (testing "> misc/verify-code"
        (testing "> error-response > wrong code"
          (let [result (request :misc/verify-code :post :form {:code "WRONG" :phone phone})]
            (expect (get-in result [:body :errors :code]))
            (expect 400 (:status result) "Expect HTTP 400"))
          (let [result (request :misc/verify-code :post :form {:code code :phone phone})]
            (expect nil (get-in result [:body :errors]) "Expect no error")
            (expect 200 (:status result) "Expect HTTP 200")))))))


(defn misc-code-login
  ([registered?]
   (misc-code-login registered? true))
  ([registered? verified?]
   (testing "> misc/code-login"
     (let [result (request :misc/code-login :post)]
       (if verified?
         (do (expect (util/handler-name->route-path app
                                                    (if registered?
                                                      :misc/root
                                                      :misc/register))
                     (-> (get-in result [:body :redirect])
                         (util/relative-path))
                     "Expect redirect depending on verified citizen status")
             (expect 200 (:status result) "Expecting HTTP 200"))
         (do (expect (util/handler-name->route-path app :misc/login)
                     (-> (get-in result [:body :redirect])
                         (util/relative-path))
                     "Expect redirect to /login")
             (expect (get-in result [:body :error]))
             (expect 401 (:status result) "Expecting HTTP 401")))))))


(defn misc-change-citizen-password [valid-form registered? verified?]
  (testing "> misc/change-password"
    (let [result (request :misc/change-password :post :form valid-form)]
      (if verified?
        (if registered?
          (do
            (expect 200 (:status result) "Expect HTTP 200")
            (expect (util/handler-name->route-path app :misc/root)
                    (-> (get-in result [:body :redirect])
                        (util/relative-path))))
          (do
            (expect 401 (:status result) "Expect HTTP 401")
            (expect (util/handler-name->route-path app :misc/register)
                    (-> (get-in result [:body :redirect])
                        (util/relative-path)))))
        (do
          (expect 400 (:status result) "Expect HTTP 400")
          (expect (get-in result [:body :errors :phone])))))))



(defn misc-staff-login-root []
  (testing "misc/staff"
    (testing "> error-response"
      (let [result (request :misc/staff :post :form {})]
        (expect (get-in result [:body :errors]))
        (expect 422 (:status result))))
    (let [result (request :misc/staff :post :form {:username "root" :password "WHATEVER"})]
      (expect nil (get-in result [:body :errors]) "Expect no errors")
      (expect 302 (:status result) "Expect redirect"))))


(defn admin-client-create [oauth-client]
  (testing "admin.client/create"
    (let [result (request :admin.client/create :post :form oauth-client)]
      (expect nil (get-in result [:body :errors]))
      (expect 302 (:status result)))
    (testing "> error-response > not unique id"
      (let [result (request :admin.client/create :post :form oauth-client)]
        (expect (get-in result [:body :errors :id]))
        (expect (empty? (dissoc (get-in result [:body :errors]) :id)))
        (expect 400 (:status result))))))


(defn admin-client-edit [oauth-client]
  (testing "admin.client/edit"
    (let [result (request :admin.client/edit :post :form oauth-client :path oauth-client)]
      (expect nil (get-in result [:body :errors]))
      (expect 302 (:status result)))
    (testing "> error-response > not found"
      (let [result (request :admin.client/edit :post :form oauth-client :path {:id (random-uuid)})]
        (expect 404 (:status result))))))


(defn misc-register [citizen]
  (testing "> misc/register"
    (testing "> error-response > no first name"
      (let [result (request :misc/register :post :form (dissoc citizen :first_name))]
        (expect (get-in result [:body :errors]))
        (expect 400 (:status result)) "Expecting HTTP 400"))

    (let [result (request :misc/register :post :form citizen)]
      (expect nil (get-in result [:body :errors]) "Expect no errors")
      (expect (util/handler-name->route-path app :citizen.report/index)
              (-> (get-in result [:headers "Location"])
                  (util/relative-path)) "Expect redirect to :citizen.report/index")
      (expect 302 (:status result) "Expect HTTP 302"))))


(defn misc-logout []
  (testing "> misc/logout"
    (let [result (request :misc/logout :post)]
      (expect 302 (:status result) "Expect HTTP 302"))))


(defn misc-root [role]
  (testing "> misc/root"
    (let [result (request :misc/root :get)
          [redirect_uri] (some->
                           (get-in result [:trace-redirects 0])
                           (util/split-uri-query))]
      (expect (util/handler-name->route-path
                app
                (case role
                  "inspector" :staff.report/index
                  "admin" :staff.statistics/index
                  "citizen" :citizen.report/index
                  :misc/login))
              (util/relative-path redirect_uri) "Expecting redirect_uri to to be valid"))))


(defn misc-citizen-status [phone]
  (testing "> misc/citizen-status"
    (let [result (request :misc/citizen-status :post :form {:phone phone})]
      (expect nil (get-in result [:body :errors]) "Expect no errors")
      (expect 200 (:status result) "Expect HTTP 302")
      (expect "registered" (get-in result [:body :status]) "Expect user status be \"registered\""))))


(defn citizen-password-login [phone password]
  (testing "misc/login"
    (testing "> error-response"
      (let [result (request :misc/login :post :form {})]
        (expect-some (get-in result [:body :errors :phone]) "Phone validation must fail")
        (expect 400 (:status result)) "Response must have Validation error Status")
      (let [result (request :misc/login :post :form {:phone phone})]
        (expect-some (get-in result [:body :errors :password]) "Password validation must fail")
        (expect 400 (:status result)) "Response must have Validation error Status")
      (let [result (request :misc/login :post :form {:phone    phone
                                                     :password "WRONG"})]
        (expect-some (get-in result [:body :errors :password]) "Password validation must fail")
        (expect 400 (:status result))) "Response must have Validation error Status")
    (let [result (request :misc/login :post :form {:phone    phone
                                                   :password password})]
      (expect nil (get-in result [:body :errors]) "Expect no errors")
      (expect 200 (:status result) "Expect redirect"))))


(deftest test-app
  (->
    (with-redefs [;; disable anti-forgery check
                  ring.middleware.anti-forgery/valid-request? (fn [& _] true)
                  ;; allow to read render params
                  jarima.layout/render (fn [_ & [params response]]
                                         ((or response bad-request) (json/encode params)))
                  jarima.layout/error-page (fn [details]
                                             (-> {:errors details}
                                                 (json/encode)
                                                 (bad-request)
                                                 (assoc-some :status (:status details))))]

      (binding [chcore/*cookie-store* (chcookie/cookie-store)]
        (with-redefs [;; Allow to login with any password
                      buddy.hashers/check (fn [_ _] true)]
          (misc-staff-login-root))
        (admin-client-create oauth-code-client)
        (admin-client-edit oauth-code-client))

      (binding [chcore/*cookie-store* (chcookie/cookie-store)]
        ;; Authorization tests starts here
        (misc-code-login false false)
        (misc-send-and-verify-code (:phone citizen))
        (misc-code-login false true)
        (misc-change-citizen-password
          {:new_password          (:password citizen)
           :repeated_new_password (:password citizen)} false true)
        (misc-register citizen)
        (misc-citizen-status (:phone citizen))
        (misc-root "citizen")
        (misc-logout)
        (misc-root nil)
        (misc-send-and-verify-code (:phone citizen))
        (misc-code-login true true)
        (misc-change-citizen-password
          {:new_password          (:password citizen)
           :repeated_new_password (:password citizen)} true false)
        (misc-send-and-verify-code (:phone citizen))
        (misc-change-citizen-password
          {:new_password          (:password citizen)
           :repeated_new_password (:password citizen)} true true)
        (misc-root "citizen")
        (citizen-password-login (:phone citizen) (:password citizen))
        ;; Authorization tests ends here
        (testing "oauth/approve"
          (testing "> error-response"
            (testing "> wrong-scope"
              (let [result (request :oauth/approve :post
                                    :form (update approvement :scope str \, "read-organization"))
                    [redirect_uri redirect_params] (some-> result
                                                           (get-in [:headers "Location"])
                                                           (util/split-uri-query))
                    redirect_params (update-existing redirect_params :errors parse-json)]
                (expect 302 (:status result) "Expecting redirect")
                (expect static-param (:static-param redirect_params) "Expecting static query param")
                (expect (:state approvement) (:state redirect_params) "State must not change")
                (expect "invalid_scope" (:error redirect_params))
                (expect (get-in redirect_params [:errors :scope]))
                (expect (empty? (dissoc (:errors redirect_params) :scope)))
                (expect (util/remove-uri-query (:error_redirect_uri oauth-code-client))
                        (util/remove-uri-query redirect_uri) "Expecting redirect_uri to be specified")
                (expect dynamic-param (:dynamic-param redirect_params) "Expect preserving dynamic param")
                (expect static-param (:static-param redirect_params) "Expect preserving static param")))
            (testing "> wrong-redirect-uri"
              (let [result (request :oauth/approve :post
                                    :form (assoc approvement :redirect_uri "WRONG"))
                    [redirect_uri redirect_params] (some-> result
                                                           (get-in [:headers "Location"])
                                                           (util/split-uri-query))
                    redirect_params (update-existing redirect_params :errors parse-json)]
                (expect 302 (:status result) "Expecting redirect")
                (expect static-param (:static-param redirect_params) "Expecting static query param")
                (expect (:state approvement) (:state redirect_params) "State must not change")
                (expect "invalid_request" (:error redirect_params))
                (expect (get-in redirect_params [:errors :redirect_uri]))
                (expect (empty? (dissoc (:errors redirect_params) :redirect_uri)))
                (expect (util/remove-uri-query (:error_redirect_uri oauth-code-client))
                        (util/remove-uri-query redirect_uri) "Expecting error redirect_uri")))

            (testing "> wrong-client-id"
              (let [result (request :oauth/approve :post
                                    :form (assoc approvement :client_id "WRONG"))]
                (expect 400 (:status result) "Expect no redirect"))))


          (testing "> default-values"
            (let [result (request :oauth/approve :post
                                  :form (assoc approvement :redirect_uri nil))
                  [redirect_uri
                   redirect_params] (some-> (get-in result [:headers "Location"])
                                            (util/split-uri-query))]

              (expect (util/remove-uri-query (first (map str/trim (str/split (:redirect_uri oauth-code-client) #","))))
                      (util/remove-uri-query redirect_uri) "Expecting redirect_uri be default")

              (expect static-param (:static-param redirect_params) "Expecting static query param")
              (expect (get redirect_params :code))

              (testing "> oauth/access_token"
                (testing "> error-response"

                  (testing "> wrong-code"
                    (let [result (request :oauth/access_token :post
                                          :form (assoc client-credentials
                                                  ;; :redirect_uri (:redirect_uri approvement)
                                                  :code (random-uuid)))]
                      (expect 400 (:status result))
                      (expect (get-in result [:body :errors :code]))
                      (expect (empty? (dissoc (get-in result [:body :errors]) :code)))
                      (expect nil (get-in result [:body :access_token]) "Expect no token")))
                  (testing "> wrong-secret"
                    (let [result (request :oauth/access_token :post
                                          :form (assoc client-credentials
                                                  ;; :redirect_uri (:redirect_uri approvement)
                                                  :code (:code redirect_params)
                                                  :client_secret (random-uuid)))]
                      (expect 400 (:status result))
                      (expect (get-in result [:body :errors :client_secret]))
                      (expect (empty? (dissoc (get-in result [:body :errors]) :client_secret)))
                      (expect nil (get-in result [:body :access_token]) "Expect no token"))))


                (let [result (request :oauth/access_token :post
                                      :form (assoc client-credentials
                                              ;; :redirect_uri (:redirect_uri approvement)
                                              :code (:code redirect_params)))
                      refresh_token (get-in result [:body :refresh_token])
                      access_token (get-in result [:body :access_token])]
                  (expect 200 (:status result))
                  (expect (get-in result [:body :access_token]))
                  (expect nil (get-in result [:body :errors]) "Expect no errors")

                  (testing "> api.report/create-form > scope error"

                    (let [result (request :api.report/create-form :get
                                          :headers {:authorization (str "Bearer " access_token)})]
                      (expect 403 (:status result))
                      (expect (get-in result [:body :errors :token_scope]))))))))


          (testing "> specified-redirect-uri-with-params"
            (let [result (request :oauth/approve :post
                                  :form (assoc approvement :scope "send-report,read-card-phone"))
                  [redirect_uri
                   redirect_params] (some-> (get-in result [:headers "Location"])
                                            (util/split-uri-query))]

              (expect (util/remove-uri-query (:redirect_uri approvement))
                      (util/remove-uri-query redirect_uri) "Expecting redirect_uri be specified in approvement")
              (expect dynamic-param (:dynamic-param redirect_params) "Expecting saved query param")
              (expect (get redirect_params :code))
              (testing "> oauth/access_token"
                (testing "> error-response"
                  (testing "> wrong-redirect-uri"
                    (let [result (request :oauth/access_token :post
                                          :form (assoc client-credentials
                                                  :redirect_uri "WRONG"
                                                  :code (:code redirect_params)))]
                      (expect 400 (:status result))
                      (expect (get-in result [:body :errors :redirect_uri]))
                      (expect (empty? (dissoc (get-in result [:body :errors]) :redirect_uri)))
                      (expect nil (get-in result [:body :access_token]) "Expect no token")))

                  (testing "> no-redirect-uri"
                    (let [result (request :oauth/access_token :post
                                          :form (assoc client-credentials
                                                  :code (:code redirect_params)))]
                      (expect 400 (:status result))
                      (expect (get-in result [:body :errors :redirect_uri]))
                      (expect (empty? (dissoc (get-in result [:body :errors]) :redirect_uri)))
                      (expect nil (get-in result [:body :access_token]) "Expect no token"))))



                (let [result (request :oauth/access_token :post
                                      :form (assoc client-credentials
                                              :redirect_uri (:redirect_uri approvement)
                                              :code (:code redirect_params)))
                      refresh_token (get-in result [:body :refresh_token])
                      access_token (get-in result [:body :access_token])]

                  (expect 200 (:status result))
                  (expect (get-in result [:body :access_token]))
                  (expect nil (get-in result [:body :errors]) "Expect no errors")
                  (testing "> on-issue-old-token-revocation"
                    (expect 1 (-> {:client_id [(uuid (:client_id approvement))]
                                   :revoked   true}
                                  (q/select-oauth-token)
                                  (dissoc :order-by)
                                  (assoc :select [:%count.*])
                                  (db/query-first :count))))

                  ;; OAUTH2 API TESTS START HERE
                  (testing "> api.report/upload-video"
                    (testing "> error-response"
                      (testing "> invalid_token"
                        (let [result (request :api.report/upload-video :post
                                              :headers {:authorization (str "Bearer " "WRONG")}
                                              :multipart [{:name "video" :content (clojure.java.io/file "resources/test.mp4")}])]
                          (expect 401 (:status result))
                          (expect "invalid_token" (get-in result [:body :error]) "Expect invalid token error")
                          (expect (get-in result [:body :error]) "invalid_token")))

                      (testing "> invalid_video"
                        (let [result (request :api.report/upload-video :post
                                              :headers {:authorization (str "Bearer " access_token)})]
                          (expect 400 (:status result))
                          (expect "invalid_request" (get-in result [:body :error]) "Expect invalid token error")
                          (expect (get-in result [:body :errors :video]))
                          (expect (empty? (dissoc (get-in result [:body :errors]) :video))))))

                    (let [result (request :api.report/upload-video :post
                                          :headers {:authorization (str "Bearer " access_token)}
                                          :multipart [{:name "video" :content (clojure.java.io/file "resources/test.mp4")}])
                          video (:body result)]
                      (expect 200 (:status result))
                      (expect (get-in result [:body :id]))
                      (expect (get-in result [:body :download-url]))
                      (expect nil (get-in result [:body :errors]) "Expect no errors")


                      (testing "> api.report/create-form"
                        (let [result (request :api.report/create-form :get
                                              :headers {:authorization (str "Bearer " access_token)})
                              create-form (:body result)]
                          (expect 200 (:status result))
                          (expect nil (get-in result [:body :organizations]) "Handler must restrict organizations by scope")
                          (expect (get-in result [:body :profile]))
                          (expect nil (get-in result [:body :errors :redirect_uri]) "Expect no errors")


                          (testing "> api.report/create"
                            (testing "> error-response"
                              (testing "> multiple-rewards"
                                (with-redefs [jarima.kash/card (constantly {})]
                                  (let [result (request :api.report/create :post
                                                        :headers {:content-type  :json
                                                                  :authorization (str "Bearer " access_token)}

                                                        :form (assoc jarima-report
                                                                :video_id (:id video)
                                                                :extra_video_id (:id video)
                                                                :reward_params
                                                                {:card  "8600 0000 0000 0000"
                                                                 :phone "+998900000000"}))]
                                    (expect 400 (:status result))
                                    (expect "invalid_request" (get-in result [:body :error]))
                                    (expect (get-in result [:body :errors :reward_params]))
                                    (expect (empty? (dissoc (get-in result [:body :errors]) :reward_params))))))

                              (testing "> invalid_card"
                                (with-redefs [jarima.kash/card (constantly {:error {:message "INVALID"}})]
                                  (let [result (request :api.report/create :post
                                                        :headers {:authorization (str "Bearer " access_token)}
                                                        :content-type :json
                                                        :form (assoc jarima-report
                                                                :video_id (:id video)
                                                                :extra_video_id (:id video)
                                                                :reward_params {"card" "8600 0000 0000 0000"}))]
                                    (expect 400 (:status result))
                                    (expect "invalid_request" (get-in result [:body :error]))
                                    (expect (get-in result [:body :errors :reward_params]))
                                    (expect (expect (empty? (dissoc (get-in result [:body :errors]) :reward_params)))))))

                              #_(testing "> service_unavailable"
                                  (with-redefs [jarima.kash/card (fn [_] (throw ""))]
                                    (let [result (request :api.report/create :post
                                                          :headers {:authorization (str "Bearer " access_token)}
                                                          :content-type :json
                                                          :form (assoc jarima-report
                                                                  :video_id (:id video)
                                                                  :extra_video_id (:id video)
                                                                  :reward_params {"card" "8600 0000 0000 0000"}))]
                                      (expect 400 (:status result))
                                      (expect "invalid_request" (get-in result [:body :error]))
                                      (expect (get-in result [:body :errors :reward_params]))
                                      (expect (empty? (dissoc (get-in result [:body :errors]) :reward_params))))))

                              (with-redefs [jarima.kash/card (fn [_] {})]
                                (let [result (request :api.report/create :post
                                                      :headers {:authorization (str "Bearer " access_token)}
                                                      :content-type :json
                                                      :form (assoc jarima-report
                                                              :video_id (:id video)
                                                              :extra_video_id (:id video)
                                                              :reward_params {"card" "8600 0000 0000 0000"}))]
                                  (expect 200 (:status result))
                                  (expect (get-in result [:body :id]))
                                  (expect nil (get-in result [:body :errors]) "Expect no errors")))
                              ;; TODO Need to unite date and time to validate properly
                              #_(testing "> expired_date"
                                  (with-redefs [jarima.kash/card (fn [_] (throw ""))]
                                    (let [result (request :api.report/create :post
                                                          :headers {:authorization (str "Bearer " access_token)}
                                                          :content-type :json
                                                          :form (assoc jarima-report
                                                                  :incident_date (util/unparse-local-date-short (.minusDays (util/now) 3))
                                                                  :incident_time (util/unparse-local-time-short (.minusDays (util/now) 3))
                                                                  :video_id (:id video)
                                                                  :extra_video_id (:id video)
                                                                  :reward_params {"card" "8600 0000 0000 0000"}))]
                                      (expect 400 (:status result))
                                      (expect "invalid_request" (get-in result [:body :error]))
                                      (expect (get-in result [:body :errors :incident_date]))
                                      (expect (empty? (dissoc (get-in result [:body :errors]) :reward_params))))))))))))
                  ;; OAUTH2 API TESTS ENDS HERE

                  (testing "> oauth/refresh_token"
                    (testing "> error-response"
                      (testing "> invalid-scope"
                        (let [result (request :oauth/refresh_token :post
                                              :form (assoc client-credentials
                                                      :scope (str (:scope approvement) \, "read-user-private")
                                                      :refresh_token refresh_token
                                                      :grant_type "refresh_token"))]
                          (expect 400 (:status result))
                          (expect (get-in result [:body :errors :scope]))
                          (expect nil (get-in result [:body :access_token]) "Expect no token")))
                      (testing "> invalid-token"
                        (let [result (request :oauth/refresh_token :post
                                              :form (assoc client-credentials
                                                      :grant_type "refresh_token"
                                                      :refresh_token (util/uuid->guid (random-uuid))))]
                          (expect 400 (:status result))
                          (expect (get-in result [:body :errors :refresh_token]))
                          (expect nil (get-in result [:body :access_token]) "Expect no token")
                          (expect nil (get-in result [:body :errors :scope]) "Expect no scope error")))
                      (testing "> expired-refresh-token"
                        ;; move-time-forward
                        (let [now (util/now)]
                          (with-redefs [util/now #(.plusSeconds now
                                                                (:refresh_token_expire_seconds env (* 24 60 60 30)))]
                            (let [result (request :oauth/refresh_token :post
                                                  :form (assoc client-credentials
                                                          :grant_type "refresh_token"
                                                          :refresh_token refresh_token))]
                              (expect 400 (:status result))
                              (expect (get-in result [:body :errors :refresh_expire_time]))
                              (expect nil (get-in result [:body :access_token]) "Expect no token")))))

                      (let [result (request :oauth/refresh_token :post
                                            :form (assoc client-credentials
                                                    :grant_type "refresh_token"
                                                    :refresh_token refresh_token))]
                        (expect 200 (:status result))
                        (expect (get-in result [:body :access_token]))
                        (expect nil (get-in result [:body :errors :redirect_uri]) "Expect no errors")))))))))))
    (with-fake-db)))

