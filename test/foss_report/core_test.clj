(ns foss-report.core-test
  (:require [clojure.test :refer [deftest are is run-tests]]
            [foss-report.core :refer :all]))

(comment
  (deftest merge-spdx-mapping-test
    (are [x y] (= x y)
      (key {"a" #{"b"}}) "a"
      (val {"a" #{"b"}}) #{"b"}))
  )

(comment
  (run-tests))
