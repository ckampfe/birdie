(def project 'birdie)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"resources"}
          :source-paths   #{"src"}
          :dependencies   '[[org.clojure/clojure "1.9.0"]
                            [org.clojure/clojurescript "RELEASE"]

                            [crisptrutski/boot-cljs-test "0.3.5-SNAPSHOT" :scope "test"]
                            [com.cemerick/piggieback "0.2.2" :scope "test"]
                            [adzerk/boot-test "RELEASE" :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "FIXME: write description"
      :url         "http://example/FIXME"
      :scm         {:url "https://github.com/yourname/birdie"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(require '[crisptrutski.boot-cljs-test :refer [test-cljs]])

(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (install)))

(deftask testing []
  (set-env! :source-paths #(conj % "test/cljs"))
  identity)

(deftask auto-test []
  (comp (testing)
        (watch)
        (test-cljs)))

#_(require '[adzerk.boot-test :refer [test]])
