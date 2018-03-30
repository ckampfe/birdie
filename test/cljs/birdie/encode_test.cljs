(ns birdie.encode-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [birdie.core :as c]
            [birdie.fixtures :as fixtures]))

(enable-console-print!)

(deftest encode-test
  (testing "binaries" ;; strings
    (is (= "foo" (c/decode (c/encode "foo"))))
    (let [s (reduce str (repeat 514 3))]
      (is (= s (c/decode (c/encode s)))))
    ))

