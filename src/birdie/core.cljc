(ns birdie.core
  (:require [birdie.decode :as d]
            [birdie.encode :as e]))

(defn encode [exp]
  (e/encode exp))

(defn decode [s]
  (d/decode s))
