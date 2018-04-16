(ns birdie.encode
  #?(:cljs (:require [goog.crypt :as crypt])))

(def max-32-bit-signed-int 2147483647)
(def min-32-bit-signed-int -2147483648)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol Encodable
  (do-encode [this]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn int-to-2-bytes [n]
  #?(:cljs (let [buf (new js/ArrayBuffer 2)
                 byte-view (new js/Uint8Array buf)
                 int-view (new js/Int16Array buf)]

             (aset int-view 0 n)

             (mapv (fn [i] (aget byte-view i))
                   (rseq (vec (range 2)))))))

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
        size-bytes (int-to-4-bytes (count bytes))]

    (persistent! (apply conj!
                        (->> size-bytes
                             (cons 109)
                             (into [])
                             transient)
                        bytes))))

(defn encode-small-atom-utf8 [bytes length]
  (cons 119 (cons length bytes)))

(defn encode-atom-utf8 [bytes length]
  (let [length-bytes (int-to-2-bytes length)]
    (cons 118 (concat length-bytes bytes))))

(defn encode-atom [exp]
  (let [bytes (string-to-byte-vector (str exp))
        length-bytes (->> bytes
                          count
                          int-to-2-bytes)]
    (cons 100 (concat length-bytes bytes))))

(defn encode-seq [exp]
  (let [elements (->> exp
                      (reduce (fn [acc val]
                                (apply conj! acc (do-encode val)))
                              (transient []))
                      persistent!)
        length-bytes (->> exp
                          count
                          int-to-4-bytes)]

    (cons 108 (concat length-bytes
                      elements
                      (vector 106)))))

(defn encode-map [exp]
  (let [length-bytes (->> exp
                          count
                          int-to-4-bytes)
        elements (->> exp
                      (reduce (fn [acc [k v]]
                                (apply conj!
                                       (apply conj! acc (do-encode k))
                                       (do-encode v)))
                              (transient []))
                      persistent!)]

    (cons 116 (concat length-bytes elements))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn encode-number [exp]
  (cond
    (is-float? exp)              (encode-new-float exp)
    (fits-in-unsigned-byte? exp) (encode-small-integer exp)
    (fits-in-4-bytes? exp)       (encode-integer exp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn encode-keyword [exp]
  (let [bytes (string-to-byte-vector (name exp))
        length (count bytes)]

    (cond
      (< length 256) (encode-small-atom-utf8 bytes length)
      (< length 65536) (encode-atom-utf8 bytes length)
      :default (throw (js/Error "Atom exceeds maximum byte-length of 65535")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-protocol Encodable
  cljs.core/Keyword
  (do-encode [this] (encode-keyword this))

  string
  (do-encode [this] (encode-string this))

  number
  (do-encode [this] (encode-number this))

  boolean
  (do-encode [this] (encode-atom this))

  cljs.core/PersistentVector
  (do-encode [this] (encode-seq this))

  cljs.core/List
  (do-encode [this] (encode-seq (reverse this)))

  cljs.core/EmptyList
  (do-encode [this] (encode-seq []))

  cljs.core/PersistentHashSet
  (do-encode [this] (encode-seq this))

  cljs.core/PersistentHashMap
  (do-encode [this] (encode-map this))

  cljs.core/PersistentArrayMap
  (do-encode [this] (encode-map this))

  boolean
  (do-encode [this] (encode-atom this))

  default
  (do-encode [this] (throw js/Error "No encoder found for" this)))

(defn encode [exp]
  (cons 131 (do-encode exp)))
