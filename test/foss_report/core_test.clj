(ns foss-report.core-test
  (:require [clojure.test :refer :all]
            [foss-report.core :refer :all]))

(deftest merge-spdx-mapping-test
  (are [x y] (= x y)
    "a" (key {"a" #{"b"}})
    #{"b"} (val {"a" #{"b"}})))

(comment
  (run-tests))
