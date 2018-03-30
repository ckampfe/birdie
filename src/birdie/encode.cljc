(ns birdie.encode
  #?(:cljs (:require [goog.crypt :as crypt])))

(defn make-state [exp]
  (atom {:exp exp
         :result []}))

(ns-unmap 'birdie.encode 'do-encode)

(defmulti do-encode (fn [exp] (type exp)))

(defmethod do-encode #?(:cljs js/String) [exp]
  (let [bytes (vec (crypt/stringToUtf8ByteArray exp))
        length (count bytes)
        identifier-byte 109
        size-byte-3 (-> 0xff
                        (bit-shift-left 31)
                        (bit-and length)
                        (bit-shift-right 31))
        size-byte-2 (-> 0xff
                        (bit-shift-left 16)
                        (bit-and length)
                        (bit-shift-right 16))
        size-byte-1 (-> 0xff
                        (bit-shift-left 8)
                        (bit-and length)
                        (bit-shift-right 8))
        size-byte-0 (bit-and 0xff length)]

    (concat (vector identifier-byte
                    size-byte-3
                    size-byte-2
                    size-byte-1
                    size-byte-0)
            bytes)))

(defn encode [exp]
  (cons 131 (do-encode exp)))

