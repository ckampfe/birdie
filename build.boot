(def project 'birdie)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"resources"}
          :source-paths   #{"src"}
          :dependencies   '[[org.clojure/clojure          "1.9.0"]
                            [org.clojure/clojurescript "1.10.238"]
                            [adzerk/boot-cljs            "2.1.4"          :scope "test"]
                            [crisptrutski/boot-cljs-test "0.3.5-SNAPSHOT" :scope "test"]
                            [cider/piggieback            "0.3.5"          :scope "test"]
                            [adzerk/boot-test            "RELEASE"        :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "FIXME: write description"
      :url         "http://example/FIXME"
      :scm         {:url "https://github.com/yourname/birdie"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(require '[adzerk.boot-cljs            :refer [cljs]]
         '[crisptrutski.boot-cljs-test :refer [test-cljs]])

(deftask production []
  (task-options! cljs {:optimizations :advanced
                       :compiler-options {:output-to "out/js/app.js"
                                          ;;:main "birdie.core"
                                          ;;:target :nodejs
                                          ;; :aot-cache true
                                          }})
  identity)

(deftask development []
  (task-options! cljs {:optimizations :none})
  identity)

(deftask build
  "Build and install the project locally."
  []
  (comp (production)
        (speak)
        (cljs)
        (target)))

(deftask testing []
  (set-env! :source-paths #(conj % "test/cljs"))
  identity)

(deftask auto-test []
  (comp (testing)
        (watch)
        (test-cljs)))
