(ns birdie.core
  (:require [birdie.decode :as d]))

(defn encode [exp])

(defn decode [s]
  (d/decode s))
