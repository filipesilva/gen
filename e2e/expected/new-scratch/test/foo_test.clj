(ns foo-test
  (:require [clojure.test :refer [deftest is testing]]))

(deftest missing
  (is false))
