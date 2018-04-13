(ns encode-bench.core
  (:require [birdie.core :as b]))

(def small-homogenous-vector [1 2 3 4 5])
(def small-homogenous-string-vector ["hi" "there" "how" "are" "you"])
(def large-homogenous-vector (into [] (range 0 10000)))
(def large-heterogenous-vector (->> [:a "b" 3 [4] {:z :t}]
                                    cycle
                                    (take 10000)
                                    (into [])))
(def small-map {:a 73 :b 8248 :c "hello" :d :ok :e 99})
(def large-map (->> (range 1 10001)
                    (partition 2)
                    (reduce (fn [acc [k v]] (assoc acc
                                                   (keyword (.toString k))
                                                   v))
                            {})))

(defn json-encode [d]
  (->> d
       clj->js
       (.stringify js/JSON)))

(def inputs [{:suite-name "ETF encode benchmarks"
              :benchmarks [{:name "ETF small homogenous vector"
                            :data small-homogenous-vector
                            :bench-fn (comp (fn [c] (dorun c)) b/encode)
                            :i 10000}
                           {:name "ETF small homogenous string vector"
                            :data small-homogenous-string-vector
                            :bench-fn (comp (fn [c] (dorun c)) b/encode)
                            :i 10000}
                           {:name "ETF large homogenous vector"
                            :data large-homogenous-vector
                            :bench-fn (comp (fn [c] (dorun c)) b/encode)
                            :i 100}
                           {:name "ETF large heterogenous vector"
                            :data large-heterogenous-vector
                            :bench-fn (comp (fn [c] (dorun c)) b/encode)
                            :i 100}
                           {:name "ETF small map"
                            :data small-map
                            :bench-fn (comp (fn [c] (dorun c)) b/encode)
                            :i 10000}
                           {:name "ETF large map"
                            :data large-map
                            :bench-fn (comp (fn [c] (dorun c)) b/encode)
                            :i 100}]}

             {:suite-name "JSON encode benchmarks"
              :benchmarks [{:name "JSON small homogenous vector"
                            :data small-homogenous-vector
                            :bench-fn json-encode
                            :i 10000}
                           {:name "JSON small homogenous string vector"
                            :data small-homogenous-string-vector
                            :bench-fn json-encode
                            :i 10000}
                           {:name "JSON large homogenous vector"
                            :data large-homogenous-vector
                            :bench-fn json-encode
                            :i 100}
                           {:name "JSON large heterogenous vector"
                            :data large-heterogenous-vector
                            :bench-fn json-encode
                            :i 100}
                           {:name "JSON small map"
                            :data small-map
                            :bench-fn json-encode
                            :i 10000}
                           {:name "JSON large map"
                            :data large-map
                            :bench-fn json-encode
                            :i 100}]}])

(defn run-benchmarks! [inputs warmup-i real-i]
  (doseq [{:keys [suite-name benchmarks]} (shuffle inputs)]

    (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    (println "running benchmark suite" suite-name)
    (println "suite contains" (count benchmarks) "benchmarks")

    (doseq [{:keys [name data bench-fn i]} benchmarks]
      (println "benchmarking" name "with" i "iterations and" warmup-i "warmup runs")

      (doseq [_ (range warmup-i)]
        (simple-benchmark [data data] (bench-fn data) i :print-fn (fn [_] nil)))

      (doseq [_ (range real-i)]
        (simple-benchmark [data data] (bench-fn data) i))

      (println))
    (println "<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
    (println)))

(run-benchmarks! inputs 5 3)
