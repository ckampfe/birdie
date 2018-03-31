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
    (is (= "😋" (c/decode (c/encode "😋")))))

  (testing "integers"
    (is (= 100 (c/decode (c/encode 100))))
    (is (= 3000 (c/decode (c/encode 3000))))
    (is (= 89000 (c/decode (c/encode 89000))))
    (is (= -89000 (c/decode (c/encode -89000))))
    (is (= -1024000 (c/decode (c/encode -1024000)))))

  ;; anything larger than 32-bit
  (testing "bignums")

  (testing "floats/doubles"
    (is (= 100.1 (c/decode (c/encode 100.1))))
    (is (= 0.00001 (c/decode (c/encode 0.00001))))
    (is (= 4000000.02222 (c/decode (c/encode 4000000.02222))))
    (is (= -4000000.02222 (c/decode (c/encode -4000000.02222)))))

  (testing "small keywords"
    (is (= :ok (c/decode (c/encode :ok))))
    (is (= (keyword "😋") (c/decode (c/encode (keyword "😋"))))))

  (testing "keywords"
    (let [big-keyword1 (keyword (reduce str (repeat 50000 "h")))]
      (is (= big-keyword1 (c/decode (c/encode big-keyword1))))))

  (testing "vectors")
  (testing "vectors as bytelists")
  (testing "maps")
  (testing "complex structures"))

