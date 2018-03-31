(ns birdie.encode
  #?(:cljs (:require [goog.crypt :as crypt])))

(def max-32-bit-signed-int 2147483647)
(def min-32-bit-signed-int -2147483648)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-state [exp]
  (atom {:exp exp
         :result []}))

(ns-unmap 'birdie.encode 'do-encode)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn int-to-2-bytes [n]
  (let [buf (new js/ArrayBuffer 2)
        byte-view (new js/Uint8Array buf)
        int-view (new js/Int16Array buf)]

    (aset int-view 0 n)

    (mapv (fn [i] (aget byte-view i))
          (rseq (vec (range 2))))))

(defn int-to-4-bytes [n]
  (let [buf (new js/ArrayBuffer 4)
        byte-view (new js/Uint8Array buf)
        int-view (new js/Int32Array buf)]

    (aset int-view 0 n)

    (mapv (fn [i] (aget byte-view i))
          (rseq (vec (range 4))))))

(defn double-to-8-bytes [n]
  (let [buf (new js/ArrayBuffer 8)
        byte-view (new js/Uint8Array buf)
        float-view (new js/Float64Array buf)]

    (aset float-view 0 n)

    (mapv (fn [i] (aget byte-view i))
          (rseq (vec (range 8))))))

(defn is-float? [n]
  (not= (js/parseInt n 10) n))

(defn fits-in-unsigned-byte? [n]
  (and (>= n 0)
       (< n 256)))

(defn fits-in-4-bytes? [n]
  (or (>= n min-32-bit-signed-int)
      (<= n max-32-bit-signed-int)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn encode-new-float [n]
  (cons 70 (double-to-8-bytes n)))

(defn encode-small-integer [n]
  (vector 97 n))

(defn encode-integer [n]
  (cons 98 (int-to-4-bytes n)))

(defn string-to-byte-vector [exp]
  (vec (crypt/stringToUtf8ByteArray exp)))

(defn encode-string [exp]
  (let [bytes (string-to-byte-vector exp)
        length (count bytes)
        size-bytes (int-to-4-bytes length)]

    (concat (cons 109 size-bytes)
            bytes)))

(defn encode-small-atom-utf8 [bytes length]
  (cons 119 (cons length bytes)))

(defn encode-atom-utf8 [bytes length]
  (let [length-bytes (int-to-2-bytes length)]
    (cons 118 (concat length-bytes bytes))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti do-encode (fn [exp] (type exp)))

#?(:cljs (defmethod do-encode js/String [exp]
           (encode-string exp)))

(defmethod do-encode js/Number [exp]
  (cond
    (is-float? exp)              (encode-new-float exp)
    (fits-in-unsigned-byte? exp) (encode-small-integer exp)
    (fits-in-4-bytes? exp)       (encode-integer exp)))

(defmethod do-encode cljs.core/Keyword [exp]
  (let [bytes (string-to-byte-vector (name exp))
        length (count bytes)]

    (cond
      (< length 256) (encode-small-atom-utf8 bytes length)
      (< length 65536) (encode-atom-utf8 bytes length)
      :default (throw (js/Error "Atom exceeds maximum byte-length of 65535")))))

(defmethod do-encode :default [exp]
  (println "DEFAULT")
  (throw (js/Error (str "No encoder found for " exp))))

(defn encode [exp]
  (cons 131 (do-encode exp)))
