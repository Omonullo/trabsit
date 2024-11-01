(ns jarima.test.validation
  (:require
    [jarima.http :refer :all]
    [jarima.config :refer [env]]
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [medley.core :refer [random-uuid]]
    [jarima.util :as util]
    [jarima.validation :as v]
    [expectations.clojure.test :refer [expect]]
    [clojure.string :as str]
    [buddy.hashers :as hashers]))




(deftest test-misc-validator
  (let [form {:phone       "998909555820"
              :code        "123123"
              :stored_code "123123"}]
    (testing "validate-phone-code-login"
      (let [validator v/validate-phone-code-login]
        (expect nil (validator form))
        (expect #{:phone :code} (set (keys (validator {}))) "Expect errors in phone and code")
        (expect (get (validator (assoc form :phone "WRONG")) :phone))
        (expect (get (validator (assoc form :code "WRONG")) :code))
        (expect (get (validator (assoc form :stored_code "DIFFERENT")) :code)))))

  (let [form {:phone          "998909555820"
              :password       "12345"
              :stored_citizen {:password (hashers/derive "12345")
                               :phone    "998909555820"}}]
    (testing "validate-phone-password-login"
      (let [validator v/validate-citizen-password-login]
        (expect nil (validator form))
        (expect :phone (ffirst (validator {})) "Expect errors in phone")
        (expect :password (ffirst (validator (dissoc form :password))) "Expect errors in password")
        (expect :phone (ffirst (validator (dissoc form :stored_citizen))) "Expect errors in phone")
        (expect [:stored_citizen :password] (ffirst (validator (assoc-in form [:stored_citizen :password] nil))) "Expect errors in :password")
        (expect :password (ffirst (validator (assoc form :password "WRONG"))) "Expect errors in password"))))


  (let [form {:phone                 "998909555820"
              :new_password          "1234567"
              :repeated_new_password "1234567"
              :stored_citizen {}}]
    (testing "validate-change-citizen-password"
      (let [validator v/validate-change-citizen-password]
        (expect nil (validator form))
        (expect :phone (ffirst (validator (dissoc form :phone))) "Expect errors in phone")
        (expect :new_password (ffirst (validator (dissoc form :new_password))) "Expect errors in password")
        (expect :stored_citizen (ffirst (validator (dissoc form :stored_citizen))) "Expect no registered errors")
        (expect :repeated_new_password (ffirst (validator (dissoc form :repeated_new_password))) "Expect errors in password")
        (expect :new_password (ffirst (validator (assoc form :new_password "12345"
                                                             :repeated_new_password "12345")))
                "Expect errors in password")
        (expect :new_password (ffirst (validator (assoc form :new_password (str "has" (:phone form) "in")
                                                             :repeated_new_password (str "has" (:phone form) "in"))))
                "Expect password error due to phone inclusion")
        (expect :new_password (ffirst (validator (assoc form :new_password (str "has" (subs (:phone form) 3) "in")
                                                             :repeated_new_password (str "has" (subs (:phone form) 3) "in"))))
                "Expect password error due to phone inclusion")
        (expect :new_password (ffirst (validator (assoc form :new_password (str "has" (subs (:phone form) 5) "in")
                                                             :repeated_new_password (str "has" (subs (:phone form) 5) "in"))))
                "Expect password error due to phone inclusion")
        (expect :new_password (ffirst (validator (assoc form :new_password "WRONG"))) "Expect errors in password matching")
        (expect :new_password (ffirst (validator (assoc form :repeated_new_password "WRONG"))) "Expect errors in password matching"))))


  (let [form {:old_password          "12345678910"
              :new_password          "123456789"
              :repeated_new_password "123456789"
              :stored_staff          {:id       (random-uuid)
                                      :password (hashers/derive "12345678910")}}]
    (testing "validate-change-staff-password"
      (let [validator v/validate-change-staff-password]
        (expect nil (validator form))
        (expect #{:old_password} (set (keys (validator (dissoc form :old_password)))) "Expect errors in old_password")
        (expect #{[:stored_staff :id]} (set (keys (validator (dissoc form :stored_staff)))) "Expect errors in staff id")
        (expect #{:old_password} (set (keys (validator (assoc form :old_password "WRONG")))) "Expect errors in old_password")
        (expect :new_password (ffirst (validator (assoc form :new_password "WRONG"))) "Expect errors in password length")
        (expect :new_password (ffirst (validator (dissoc form :new_password))) "Expect errors in missing password")
        (expect :repeated_new_password (ffirst (validator (dissoc form :repeated_new_password))) "Expect errors in missing password")))))





(deftest test-oauth-validators
  (let [client {:client_id      (random-uuid)
                :client_secret  (util/uuid->guid (random-uuid))
                :name           "TEST APP"
                :allowed_scope  ["send-report"]
                :grant_type     "code"
                :default_scope  ["send-report"]
                :redirect_uri   ["localhost"]
                :url            "telegram.org"
                :logo           "telegram.org/logo"
                :client_enabled false}]
    (testing "client credentials"
      (let [validator v/validate-oauth-client-credentials]
        (is (= :client_id (last (ffirst (validator {})))))
        (is (= :client_secret (last (ffirst (validator {:client_id "123"})))))

        (is (= :client_secret (last (ffirst (validator {:client_id     "WRONG"
                                                        :client_secret "WRONG"
                                                        :stored_client nil})))))
        (is (= :client_enabled (last (ffirst (validator
                                               {:client_id     (:client_id client)
                                                :client_secret (:client_secret client)
                                                :stored_client client})))))

        (is (nil? (validator
                    {:client_id     (:client_id client)
                     :client_secret (:client_secret client)
                     :stored_client (assoc client :client_enabled true)})))))
    (testing "auth-request"
      (let [validator v/validate-oauth-request
            valid {:client_id     "EXIST"
                   :response_type "code"
                   :redirect_uri  "uri"
                   :stored_client {:redirect_uri  ["uri"]
                                   :client_id     "EXIST"
                                   :allowed_scope ["send-report"]}
                   :scope         ["send-report"]}]
        (is (nil? (validator valid)))
        (is (nil? (validator (dissoc valid :scope))))
        (is (nil? (validator (dissoc valid :redirect_uri))))


        (is (= [:invalid_request :client_id] (ffirst (validator (dissoc valid :client_id)))))
        (is (= [:invalid_request :stored_client :client_id] (ffirst (validator (dissoc valid :stored_client)))))

        (is (= [:invalid_request :response_type] (ffirst (validator (dissoc valid :response_type)))))
        (is (= [:invalid_request :redirect_uri] (ffirst (validator (assoc valid :redirect_uri "WRONG")))))

        (is (= [:invalid_request :state] (ffirst (validator (assoc valid :state (str/join "," (range 33)))))))

        (is (= [:unsupported_response_type :response_type]
               (ffirst (validator (assoc valid :response_type "WRONG")))))

        (is (= [:invalid_scope :scope] (ffirst (validator (-> (assoc valid :scope "WRONG"))))))

        (is (= [:invalid_scope :scope]
               (ffirst (validator (-> (assoc valid :scope "WRONG"))))))))


    (testing "access-token"
      (let [validator v/validate-oauth-access-token
            valid {:code          "123456"
                   :grant_type    "code"
                   :client_id     "123"
                   :redirect_uri  "URI"
                   :stored_token  {:code          "123456"
                                   :client_id     "123"
                                   :redirect_uri  "URI"
                                   :response_type "code"}
                   :stored_client {:grant_type "code"}}]
        (is (nil? (validator valid)))

        (is (= [:invalid_grant :redirect_uri] (ffirst (validator (dissoc valid :redirect_uri)))))
        (is (= [:invalid_grant :stored_token :code] (ffirst (validator (dissoc valid :stored_token)))))
        (is (= [:invalid_grant :client_id] (ffirst (validator (assoc valid :client_id "WRONG")))))
        (is (= [:unsupported_grant_type :grant_type] (ffirst (validator (assoc valid :grant_type "WRONG")))))))


    (testing "refresh-token"
      (let [validator v/validate-oauth-refresh-token
            valid {:refresh_token "123"
                   :grant_type    "refresh_token"
                   :stored_token  {:refresh_token       "123"
                                   :revoked             false
                                   :refresh_expire_time (.plusDays (util/now) 2)
                                   :scope               ["send-report"]
                                   :client_enabled      true}
                   :scope         ["send-report"]}]
        (is (nil? (validator valid)))
        (is (nil? (validator (dissoc valid :scope))))
        (is (= [:invalid_scope :scope] (ffirst (validator (assoc valid :scope ["WRONG"])))))
        (is (= [:invalid_request :grant_type] (ffirst (validator (dissoc valid :grant_type)))))
        (is (= [:invalid_request :refresh_token] (ffirst (validator (dissoc valid :refresh_token)))))
        (is (= [:unsupported_grant_type :grant_type] (ffirst (validator (assoc valid :grant_type "WRONG")))))
        (is (= [:invalid_grant :stored_token :refresh_token] (ffirst (validator (dissoc valid :stored_token)))))
        (is (= [:invalid_grant :stored_token :revoked] (ffirst (validator (assoc-in valid [:stored_token :revoked] true)))))
        (is (= [:invalid_grant :stored_token :refresh_expire_time]
               (ffirst (validator
                         (assoc-in valid [:stored_token :refresh_expire_time]
                                   (.minusDays (util/now) 1))))))
        (is (= [:invalid_grant :stored_token :client_enabled]
               (ffirst (validator
                         (assoc-in valid [:stored_token :client_enabled] false)))))))


    (testing "oauth-scope"
      (let [validator v/validate-oauth-scope
            valid {:resource_scope            ["send-report"]
                   :client_allowed_scope      ["send-report" "read-user-private"],
                   :token_scope               ["send-report"]}]

        (is (nil? (validator valid)))
        (is (nil? (validator (dissoc valid :scope))))
        (is (= [:insufficient_scope :client_allowed_scope]
               (ffirst (validator (assoc valid
                                    :client_allowed_scope ["read-user-private"])))))
        (is (= [:insufficient_scope :token_scope]
               (ffirst (validator (assoc valid
                                    :token_scope ["read-user-private"])))))))
    (testing "oauth-client-request"
      (let [validator v/validate-oauth-client-request
            valid {:token_client_id           "some id",
                   :client_id                 "some id"
                   :role                      "oauth"
                   :token_revoked             false,
                   :token_refresh_expire_time (.plusDays (util/now) 1),
                   :token_access_expire_time  (.plusDays (util/now) 1),
                   :client_enabled            true,
                   :resource_scope            ["send-report"]
                   :client_allowed_scope      ["send-report" "read-user-private"],
                   :token_scope               ["send-report"]}]

        (is (nil? (validator valid)))
        (is (= [:invalid_token :client_id]
               (ffirst (validator {}))))
        (is (= [:invalid_token :token_revoked]
               (ffirst (validator (assoc valid :token_revoked true)))))

        (is (= [:invalid_token :token_refresh_expire_time]
               (ffirst (validator (assoc valid
                                    :token_refresh_expire_time (.minusDays (util/now) 1))))))

        (is (= [:invalid_token :token_access_expire_time]
               (ffirst (validator (assoc valid
                                    :token_access_expire_time (.minusDays (util/now) 1))))))))))

