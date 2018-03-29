(ns birdie.core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [birdie.core :as c]
            [birdie.decode :as d]
            [birdie.fixtures :as fixtures]))

(enable-console-print!)

(deftest decode-test
  (testing "binaries" ;; everyone else calls these strings
    (is (= "fine" (c/decode (vector 131 109 0 0 0 4 102 105 110 101))))
    (is (= "ök" (c/decode (vector 131 109 0 0 0 3 195 182 107)))))

  (testing "small integers"
    (is (= 0 (c/decode (vector 131 97 0))))
    (is (= 1 (c/decode (vector 131 97 1)))))

  (testing "integers"
    (is (= 1000 (c/decode (vector 131 98 0 0 3 232))))
    (let [max-32-bit-int 2147483647
          min-32-bit-int -2147483648
          some-random-negative-number -1147443]

      (is (= max-32-bit-int (c/decode (vector 131 98 127 255 255 255))))
      (is (= min-32-bit-int (c/decode (vector 131 98 128 0 0 0))))
      (is (= some-random-negative-number (c/decode (vector 131 98 255 238 125 205))))))

  (testing "small bignums"
    (is (= 1000000000000 (c/decode (vector 131 110 5 0 0 16 165 212 232))))
    (is (= -1000000000000 (c/decode (vector 131 110 5 1 0 16 165 212 232))))
    )

  (testing "large bignums"
    (is (= 1000000000000000000000000000000000000000000000 (c/decode fixtures/large-bignum)))
    (is (= 1000000000000000000000000000000000000000000000 (c/decode fixtures/large-bignum)))

    )

  (testing "floats"
    (is (= 2.0 (c/decode (vector 131 70 64 0 0 0 0 0 0 0))))
    (is (= 23000.000000002 (c/decode (vector 131 70 64 214 118 0 0 0 2 38)))))

  (testing "atom (regular)"
    (is (= :a (c/decode (vector 131 100 0 1 97))))
    (is (= :ok (c/decode (vector  131 100 0 2 111 107)))))

  (testing "small atom utf8"
    (is (= (keyword (apply str "😋" (repeat 52 "a")))
           (c/decode (apply vector (concat (vector 131 118 1 2 240 159 152 139)
                                           (repeat 52 97)))))))

  (testing "atom utf8"
    (is (= (keyword (apply str "😋" (repeat 252 "a")))
           (c/decode (apply vector (concat (vector 131 118 1 2 240 159 152 139)
                                           (repeat 252 97)))))))

  (testing "small tuples"
    (is (= [7] (c/decode (vector 131 104 1 97 7))))
    (is (= [1 2 3] (c/decode (vector 131 104 3 97 1 97 2 97 3))))
    (is (= [1 :a "hi"] (c/decode (vector 131 104 3 97 1 100 0 1 97 109 0 0 0 2 104 105))))
    (is (= [1 [:a :b] 2.2] (c/decode (vector  131 104 3 97 1 104 2 100 0 1 97 100 0 1 98 70 64 1 153 153 153 153 153 154)))))

  (testing "large tuples"
    (let [large-seq (range 0 256)]
      (is (= (vec large-seq) (c/decode fixtures/zero-to-255)))))

  (testing "list"
    (is (= [1 :a "hello"] (c/decode (vector 131 108 0 0 0 3 97 1 100 0 1 97 109 0 0 0 5 104 101 108 108 111 106))))

    (let [large-seq (range 0 257)]
      (is (= (vec large-seq)
             (c/decode fixtures/zero-to-256)))))

  (testing "bytelist"
    (is (= [1 2 3] (c/decode (vector 131 107 0 3 1 2 3))))))
