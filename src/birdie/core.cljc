(ns birdie.core
  (:require [birdie.decode :as d]
            [birdie.encode :as e]))

(defn ^:export encode [exp]
  (e/encode exp))

(defn ^:export decode [s]
  (d/decode s))
