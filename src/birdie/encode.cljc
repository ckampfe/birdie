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

             (array
              (aget byte-view 1)
              (aget byte-view 0)))))

(defn int-to-4-bytes [n]
  (let [buf (new js/ArrayBuffer 4)
        byte-view (new js/Uint8Array buf)
        int-view (new js/Int32Array buf)]

    (aset int-view 0 n)

    (array
     (aget byte-view 3)
     (aget byte-view 2)
     (aget byte-view 1)
     (aget byte-view 0))))

(defn double-to-8-bytes [n]
  (let [buf (new js/ArrayBuffer 8)
        byte-view (new js/Uint8Array buf)
        float-view (new js/Float64Array buf)]

    (aset float-view 0 n)

    (array
     (aget byte-view 7)
     (aget byte-view 6)
     (aget byte-view 5)
     (aget byte-view 4)
     (aget byte-view 3)
     (aget byte-view 2)
     (aget byte-view 1)
     (aget byte-view 0))))

(defn ^boolean is-float? [n]
  (not= (js/parseInt n 10) n))

(defn ^boolean fits-in-unsigned-byte? [n]
  (and (>= n 0)
       (< n 256)))

(defn ^boolean fits-in-4-bytes? [n]
  (or (>= n min-32-bit-signed-int)
      (<= n max-32-bit-signed-int)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn encode-new-float [n]
  (let [arr (double-to-8-bytes n)]
    (.unshift arr 70)
    arr))

(defn encode-small-integer [n]
  (array 97 n))

(defn encode-integer [n]
  (let [arr (int-to-4-bytes n)]
    (.unshift arr 98)
    arr))

(defn string-to-byte-vector [exp]
  (crypt/stringToUtf8ByteArray exp))

(defn encode-string [exp]
  (let [bytes (string-to-byte-vector exp)
        size-bytes (int-to-4-bytes (.-length bytes))]

    (.unshift bytes (aget size-bytes 3))
    (.unshift bytes (aget size-bytes 2))
    (.unshift bytes (aget size-bytes 1))
    (.unshift bytes (aget size-bytes 0))
    (.unshift bytes 109)

    bytes))

(defn encode-small-atom-utf8 [bytes length]
  (.unshift bytes length)
  (.unshift bytes 119)
  bytes)

(defn encode-atom-utf8 [bytes length]
  (.apply (.-unshift bytes)
          bytes
          (int-to-2-bytes length))

  (.unshift bytes 118)

  bytes)

(defn encode-atom [exp]
  (let [bytes (string-to-byte-vector (.toString exp))
        length-bytes (int-to-2-bytes (.-length bytes))]

    (.apply (.-unshift bytes)
            bytes
            (int-to-2-bytes (.-length bytes)))

    (.unshift bytes 100)

    bytes))

(defn encode-indexed [exp]
  (let [length-bytes (->> exp
                          count
                          int-to-4-bytes)
        n (count exp)]

    (loop [i 0]
      (if (< i n)
        (do
          (.apply (.-push length-bytes)
                  length-bytes (do-encode (nth exp i)))
          (recur (inc i)))))

    (.unshift length-bytes 108)
    (.push length-bytes 106)
    length-bytes))

(defn encode-seq [exp]
  (let [length-bytes (->> exp
                          count
                          int-to-4-bytes)]

    (doseq [el exp]
      (.apply (.-push length-bytes)
              length-bytes (do-encode el)))

    (.unshift length-bytes 108)
    (.push length-bytes 106)
    length-bytes))

(defn encode-map [exp]
  (let [length-bytes (->> exp
                          count
                          int-to-4-bytes)]

    (doseq [[k v] exp]
      (.apply (.-push length-bytes)
              length-bytes (do-encode k))
      (.apply (.-push length-bytes)
              length-bytes (do-encode v)))

    (.unshift length-bytes 116)
    length-bytes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn encode-number [exp]
  (cond
    (is-float? exp)              (encode-new-float exp)
    (fits-in-unsigned-byte? exp) (encode-small-integer exp)
    (fits-in-4-bytes? exp)       (encode-integer exp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn encode-keyword [exp]
  (let [bytes (string-to-byte-vector (name exp))
        length (.-length bytes)]

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
  (do-encode [this] (encode-indexed this))

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

  default
  (do-encode [this] (throw js/Error "No encoder found for" this)))

(defn encode [exp]
  (let [arr (do-encode exp)]
    (.unshift arr 131)
    arr))
