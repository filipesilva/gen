(ns gen.config-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [filipesilva.gen.config :as config]
            [medley.core :as m]))

(defn mock-slurp [file-map]
  (fn [path]
    (let [path (str path)]
      (or (get file-map path)
          (throw (ex-info "tried to get non-mocked path" {:path path}))))))

(defn compose-configs [file-map m]
  (with-redefs [slurp                 (mock-slurp (m/map-vals str file-map))
                fs/exists?            (constantly true) ;; in compose-configs
                fs/directory?         (constantly true) ;; in resolve-from-path
                config/default-config {}]
    (config/compose-configs m)))

(deftest compose-configs-test
  (testing "uses from config"
    (is (= {:source "/tmp/from"
            :dest   "/tmp/to"
            :vars   {:a 1, :dest-name "to"}}
           (compose-configs
            {"/tmp/from/gen.edn" {:vars {:a 1}}}
            {:source "/tmp/from"
             :dest   "/tmp/to"}))))

  (testing "uses local config"
    (is (= {:source "/tmp/local"
            :dest   "/tmp/to"
            :vars   {:a 1, :dest-name "to"}}
           (compose-configs
            {"/tmp/local/gen.edn" {:vars {:a 1}}}
            {:source     "/tmp/to"
             :gen/config "/tmp/local/gen.edn"}))))

  (testing "deep merges vars"
    (is (= {:source "/tmp/from"
            :dest   "/tmp/to"
            :vars   {:a 1, :b 2, :c 3, :d 4, :dest-name "name"}}
           (compose-configs
            {"/tmp/from/gen.edn"   {:vars {:a 1}}
             "/tmp/local/gen.edn"  {:vars {:b 2}}
             "/tmp/global/gen.edn" {:vars {:c 3}}}
            {:source            "/tmp/from"
             :dest              "/tmp/to"
             :vars              {:d 4, :dest-name "name"}
             :gen/config        "/tmp/local/gen.edn"
             :gen/global-config "/tmp/global/gen.edn"}))))

  (testing "uses from aliases"
    (is (= {:source "/tmp/from"
            :dest   "/tmp/to"
            :vars   {:dest-name "to"}}
           (compose-configs
            {"/tmp/from/gen.edn"   {}
             "/tmp/global/gen.edn" {:sources {:f "/tmp/from"}}}
            {:source            "f" :dest "/tmp/to"
             :gen/global-config "/tmp/global/gen.edn"}))))

  (testing "uses aliases"
    (is (= {:source "/tmp/from"
            :dest   "/tmp/to"
            :vars   {:dest-name "to", :a 1, :b 2, :c 3}}
           (compose-configs
            {"/tmp/from/gen.edn" {:vars    {:a 1}
                                  :aliases {:a1 {:vars {:b 2}}
                                            :a2 {:vars {:c 3}}}}}
            {:source "/tmp/from:a1:a2"
             :dest   "/tmp/to"}))))

  (testing "uses default alias if present"
    (is (= {:source "/tmp/from"
            :dest   "/tmp/to"
            :vars   {:dest-name "to", :a 1, :b 2}}
           (compose-configs
            {"/tmp/from/gen.edn" {:vars          {:a 1}
                                  :default-alias :a1
                                  :aliases       {:a1 {:vars {:b 2}}}}}
            {:source "/tmp/from"
             :dest   "/tmp/to"})))))
