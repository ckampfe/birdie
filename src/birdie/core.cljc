(ns birdie.core
  (:require [birdie.decode :as d]
            [birdie.encode :as e]))

(defn ^:export encode
  ([exp]
   (encode exp nil))
  ([exp opts]
   (let [kind (:kind opts)
         res (cons 131 (e/encode exp))]

     (cond
       (or (= "array" kind)
           (= :array  kind)) (clj->js res)

       (or (= "typed-array" kind)
           (= :typed-array kind)) (new js/Uint8Array (clj->js res))

       :default res))))

(defn ^:export encode-js
  ([exp] (encode-js exp (clj->js nil)))
  ([exp opts] (encode (js->clj exp)
                      (js->clj opts :keywordize-keys true))))

(defn ^:export decode [s]
  (d/decode s))
