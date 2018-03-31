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
    (let [big-keyword1 (keyword (reduce str (repeat 50000 "h")))
          big-keyword-with-uft8 (keyword (reduce str (repeat 10000 "😋h")))
          too-big-keyword (keyword (reduce str (repeat 80000 "h")))]
      (is (= big-keyword1 (c/decode (c/encode big-keyword1))))
      (is (= big-keyword-with-uft8 (c/decode (c/encode big-keyword-with-uft8))))
      (is (thrown? js/Error (c/encode too-big-keyword)))))

  (testing "vectors"
    (is (= [] (c/decode (c/encode []))))
    (is (= [1 2 3] (c/decode (c/encode [1 2 3])))))

  ;; bytelists are an optimization that cuts down on encoding overhead,
  ;; encoding a bytelist may not be statically determinable,
  ;; might require an option passed in, or similar
  (testing "vectors as bytelists")

  (testing "sets"
    (is (= #{} (into #{} (c/decode (c/encode #{})))))
    (is (= #{1 2 3} (into #{} (c/decode (c/encode #{1 2 3}))))))

  (testing "lists"
    (is (= '() (into '() (c/decode (c/encode '())))))
    (is (= '(1 2 3) (into '() (c/decode (c/encode '(1 2 3)))))))

  (testing "maps"
    (let [big-map (reduce (fn [acc [k v]]
                            (assoc acc k v))
                          (hash-map)
                          (partition 2 (vec (range 1001))))]

      ;; small maps are array maps
      (is (= {} (c/decode (c/encode {}))))
      (is (= {:a 1} (c/decode (c/encode {:a 1}))))

      ;; big maps are hash maps
      (is (= big-map (c/decode (c/encode big-map))))))

  (testing "complex structures"))

