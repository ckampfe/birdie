(ns birdie.decode
  #?(:cljs (:require [goog.crypt :as crypt])))

(declare dispatch)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def EIGHT_BYTES #?(:cljs (new js/Uint8Array (new js/ArrayBuffer 8))))
(def EIGHT_BYTE_DV #?(:cljs (new js/DataView (.-buffer EIGHT_BYTES))))

(def FOUR_BYTES #?(:cljs (new js/Uint8Array (new js/ArrayBuffer 4))))
(def FOUR_BYTE_DV #?(:cljs (new js/DataView (.-buffer FOUR_BYTES))))

(def TWO_BYTES #?(:cljs (new js/Uint8Array (new js/ArrayBuffer 2))))
(def TWO_BYTE_DV #?(:cljs (new js/DataView (.-buffer TWO_BYTES))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn signed-int-from-4-bytes [byte-array]
  (aset FOUR_BYTES 0 (aget byte-array 0))
  (aset FOUR_BYTES 1 (aget byte-array 1))
  (aset FOUR_BYTES 2 (aget byte-array 2))
  (aset FOUR_BYTES 3 (aget byte-array 3))
  (.getInt32 FOUR_BYTE_DV 0))

(defn unsigned-int-from-4-bytes [byte-array]
  (aset FOUR_BYTES 0 (aget byte-array 0))
  (aset FOUR_BYTES 1 (aget byte-array 1))
  (aset FOUR_BYTES 2 (aget byte-array 2))
  (aset FOUR_BYTES 3 (aget byte-array 3))
  (.getUint32 FOUR_BYTE_DV 0))

(defn unsigned-int-from-2-bytes [byte-array]
  (aset TWO_BYTES 0 (aget byte-array 0))
  (aset TWO_BYTES 1 (aget byte-array 1))
  (.getUint16 TWO_BYTE_DV 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn take-bytes! [n state]
  (let [bytes (make-array n)]
    (loop [i 0
           pos (.-position state)]
      (if (< i n)
        (do
          (aset bytes i (nth (.-bytes state) pos))
          (recur (inc i)
                 (inc pos)))
        (do
          (set! (.-position state) pos)
          bytes)))))

(defn take-byte! [state]
  (aget (take-bytes! 1 state)
        0))

(defn add-to-result! [exp state]
  (set! (.-result state) exp)
  state)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-small-integer [state]
  (-> state
      take-byte!
      (add-to-result! state)))

(defn decode-integer [state]
  (add-to-result! (signed-int-from-4-bytes (take-bytes! 4 state))
                  state))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-small-big [state]
  (let [length (take-byte! state)
        sign-byte (take-byte! state)
        digits (take-bytes! length state)
        n (reduce (fn [acc val] (+ acc
                                   (* (get digits val)
                                      (.pow js/Math 256 val))))
                  0
                  (range length))]

    (add-to-result! (if (= 1 sign-byte) (- 0 n) n)
                    state)))

(defn decode-large-big [state]
  (let [length (unsigned-int-from-4-bytes (take-bytes! 4 state))
        sign-byte (take-byte! state)
        digits (take-bytes! length state)
        n (reduce (fn [acc val]
                    (let [value (+ acc
                                   (* (get digits val)
                                      (.pow js/Math 256 val)))]
                      (if (= value js/Infinity)
                        (reduced value)
                        value)))
                  0
                  (range length))]

    (if (= n js/Infinity)
      (add-to-result! (if (= 1 sign-byte) js/-Infinity n) state)
      (add-to-result! (if (= 1 sign-byte) (- 0 n) n)
                      state))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-binary [state]
  (let [length #?(:cljs (unsigned-int-from-4-bytes (take-bytes! 4 state)))]
    (add-to-result! (crypt/utf8ByteArrayToString (take-bytes! length state))
                    state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-new-float [state]
  (let [byte-array (take-bytes! 8 state)]
    (aset EIGHT_BYTES 0 (aget byte-array 0))
    (aset EIGHT_BYTES 1 (aget byte-array 1))
    (aset EIGHT_BYTES 2 (aget byte-array 2))
    (aset EIGHT_BYTES 3 (aget byte-array 3))
    (aset EIGHT_BYTES 4 (aget byte-array 4))
    (aset EIGHT_BYTES 5 (aget byte-array 5))
    (aset EIGHT_BYTES 6 (aget byte-array 6))
    (aset EIGHT_BYTES 7 (aget byte-array 7))
    (add-to-result! (.getFloat64 EIGHT_BYTE_DV 0)
                    state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn common-atom [state]
  (let [length (unsigned-int-from-2-bytes (take-bytes! 2 state))]
    (->> (take-bytes! length state)
         crypt/utf8ByteArrayToString
         keyword)))

(defn decode-atom [state]
  (let [kw (common-atom state)]
    (case kw
      :true (add-to-result! true state)
      :false (add-to-result! false state)
      (add-to-result! kw state))))

(defn decode-small-atom-utf8 [state]
  (let [length (take-byte! state)]
    (-> (take-bytes! length state)
        crypt/utf8ByteArrayToString
        keyword
        (add-to-result! state))))

(defn decode-atom-utf8 [state]
  (add-to-result! (common-atom state)
                  state))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-small-tuple [state]
  (let [length (take-byte! state)
        elements (loop [i 0
                        c (transient [])]
                   (if (< i length)
                     (recur (inc i)
                            (conj! c (.-result (dispatch state))))
                     (persistent! c)))]

    (add-to-result! elements state)))

(defn decode-large-tuple [state]
  (let [length (unsigned-int-from-4-bytes (take-bytes! 4 state))
        elements (loop [i 0
                        c (transient [])]
                   (if (< i length)
                     (recur (inc i)
                            (conj! c (.-result (dispatch state))))
                     (persistent! c)))]

    (add-to-result! elements state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-nil [state]
  (add-to-result! [] state))

(defn decode-string [state]
  (let [length (unsigned-int-from-2-bytes (take-bytes! 2 state))
        elements (vec (take-bytes! length state))]

    (add-to-result! elements state)))

(defn decode-list [state]
  (let [length (unsigned-int-from-4-bytes (take-bytes! 4 state))
        elements (loop [i 0
                        c (transient [])]
                   (if (< i length)
                     (recur (inc i)
                            (conj! c (.-result (dispatch state))))
                     c))
        tail (.-result (dispatch state))]

    ;; proper list has [] as tail, improper has anything else
    (add-to-result! (if (= [] tail)
                      (persistent! elements)
                      (persistent! (conj! elements tail)))
                    state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-map [state]
  (let [number-of-kv-pairs (->> state
                                (take-bytes! 4)
                                unsigned-int-from-4-bytes)
        elements (loop [i 0
                        c (transient {})]
                   (if (< i number-of-kv-pairs)
                     (recur (inc i)
                            (assoc! c
                                    (.-result (dispatch state))
                                    (.-result (dispatch state))))
                     (persistent! c)))]

    (add-to-result! elements state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-default [b]
  (throw (js/Error "No decoder found for" b)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn dispatch [state]
  (case (take-byte! state)
    70 (decode-new-float state)
    ;; 77 decode-bit-binary
    ;; 82 decode-atom-cache-reference-index
    97 (decode-small-integer state)
    98 (decode-integer state)
    ;; 99 (decode-float state)
    100 (decode-atom state)
    ;; 101 decode-reference
    ;; 102 decode-port
    ;; 103 decode-pid
    104 (decode-small-tuple state)
    105 (decode-large-tuple state)
    106 (decode-nil state)
    107 (decode-string state)
    108 (decode-list state)
    109 (decode-binary state)
    110 (decode-small-big state)
    111 (decode-large-big state)
    ;; 112 decode-new-fun
    ;; 113 decode-export
    ;; 114 decode-new-reference
    ;; 115 (decode-small-atom state)
    116 (decode-map state)
    ;; 117 decode-fun
    118 (decode-atom-utf8 state)
    119 (decode-small-atom-utf8 state)
    (decode-default state)))

(defrecord State [bytes result position])

(defn make-state [bytes]
  (State. (vec bytes) [] 0))

(defn decode [s]
  (let [state (make-state s)]
    (assert (= 131 (take-byte! state)))
    (dispatch state)
    (.-result state)))
