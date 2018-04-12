(ns birdie.decode-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [birdie.core :as c]
            [birdie.fixtures :as fixtures]
            [birdie.decode :as d]))

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
    (is (= -1000000000000 (c/decode (vector 131 110 5 1 0 16 165 212 232)))))

  (testing "large bignums"
    (is (= js/Infinity (c/decode fixtures/large-bignum)))
    (is (=  js/-Infinity (c/decode fixtures/tiny-large-bignum))))

  (testing "floats"
    (is (= 2.0 (c/decode (vector 131 70 64 0 0 0 0 0 0 0))))
    (is (= 23000.000000002 (c/decode (vector 131 70 64 214 118 0 0 0 2 38)))))

  (testing "atom (regular)"
    (is (= :a (c/decode (vector 131 100 0 1 97))))
    (is (= :ok (c/decode (vector  131 100 0 2 111 107)))))

  (testing "small atom utf8"
    (let [utf8-kw (apply str "😋" (repeat 52 "a"))]
      (is (= utf8-kw
             (c/decode (c/encode utf8-kw))))))

  (testing "atom utf8"
    (let [utf8-kw (keyword (apply str "😋" (repeat 52 "a")))]
      (is (= utf8-kw
             (c/decode (c/encode utf8-kw))))))

  (testing "small tuples"
    (is (= [7] (c/decode (vector 131 104 1 97 7))))
    (is (= [1 2 3] (c/decode (vector 131 104 3 97 1 97 2 97 3))))
    (is (= [1 :a "hi"] (c/decode (vector 131 104 3 97 1 100 0 1 97 109 0 0 0 2 104 105))))
    (is (= [1 [:a :b] 2.2] (c/decode (vector  131 104 3 97 1 104 2 100 0 1 97 100 0 1 98 70 64 1 153 153 153 153 153 154)))))

  (testing "large tuples"
    (let [large-seq (range 0 256)]
      (is (= (vec large-seq) (c/decode fixtures/zero-to-255)))))

  (testing "empty list aka NIL"
    (is (= [] (c/decode (vector 131 106)))))

  (testing "booleans"
    (is (= true (c/decode (vector 131 100 0 4 116 114 117 101))))
    (is (= false (c/decode (vector 131 100 0 5 102 97 108 115 101)))))

  (testing "lists"
    (is (= [1 :a "hello"] (c/decode (vector 131 108 0 0 0 3 97 1 100 0 1 97 109 0 0 0 5 104 101 108 108 111 106))))

    (let [large-seq (range 0 257)]
      (is (= (vec large-seq)
             (c/decode fixtures/zero-to-256))))

    ;; iex(42)> :erlang.term_to_binary(["hi" | 7])
    ;; <<131, 108, 0, 0, 0, 1, 109, 0, 0, 0, 2, 104, 105, 97, 7>>
    ;; handles improper lists
    (is (= ["hi" 7] (c/decode (vector 131, 108, 0, 0, 0, 1, 109, 0, 0, 0, 2, 104, 105, 97, 7)))))

  (testing "bytelist"
    (is (= [1 2 3] (c/decode (vector 131 107 0 3 1 2 3)))))

  (testing "maps"

    (is (= {:hi "there"} (c/decode (vector 131 116 0 0 0 1 100 0 2 104 105 109 0 0 0 5 116 104 101 114 101))))

    ;; %{:hi => "there", [1,2,3] => [4,5,6]}
    (is (= {:hi "there" [1 2 3] [4 5 6]}
           (c/decode (vector 131 116 0 0 0 2 100 0 2 104 105 109 0 0 0 5 116 104 101 114 101 107 0 3 1 2 3 107 0 3 4 5 6))))

    ;; %{first: %{a: :b}, second: %{c: :d}}
    (is (= {:first {:a :b} :second {:c :d}} (c/decode (vector 131 116 0 0 0 2 100 0 5 102 105 114 115 116 116 0 0 0 1 100 0 1 97 100 0 1 98 100 0 6 115 101 99 111 110 100 116 0 0 0 1 100 0 1 99 100 0 1 100)))))

  (testing "complex structures"
    ;; [1,2,3,:a,:b, [{:hi, :there}, {:hello, :again, :my_friend}], 7777777277274772, 101010.2424242]
    (is (= [1 2 3 :a :b [[:hi :there] [:hello :again :my_friend]] 7777777277274772 101010.2424242]
           (c/decode (vector
                      131 108 0 0 0 8 97 1 97 2 97 3 100 0 1 97 100 0 1 98 108 0 0 0 2 104 2 100 0 2 104 105 100 0 5 116 104 101 114 101 104 3 100 0 5 104 101 108 108 111 100 0 5 97 103 97 105 110 100 0 9 109 121 95 102 114 105 101 110 100 106 110 7 0 148 10 193 227 216 161 27 70 64 248 169 35 224 248 50 172 106))))

    (is (= {:a [1 2 3] :b [{} {:z :z} 777777]}
           (c/decode (vector 131 116 0 0 0 2 100 0 1 97 107 0 3 1 2 3 100 0 1 98 108 0 0 0 3 116 0 0 0 0 116 0 0 0 1 100 0 1 122 100 0 1 122 98 0 11 222 49 106))))))

