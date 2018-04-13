(ns birdie.decode
  #?(:cljs (:require [goog.crypt :as crypt])))

(declare dispatch)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs (defn signed-int-from-4-bytes [byte-array]
           (let [arr (new js/Uint8Array byte-array)
                 buf (.-buffer arr)
                 dv (new js/DataView buf)]
             (.getInt32 dv 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs (defn unsigned-int-from-2-bytes [byte-array]
           (let [arr (new js/Uint8Array byte-array)
                 buf (.-buffer arr)
                 dv (new js/DataView buf)]
             (.getUint16 dv 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn take-bytes! [n state]
  (loop [i 0
         bytes (transient [])
         pos (.-position state)]
    (if (< i n)
      (recur (inc i)
             (conj! bytes (nth (.-bytes state) pos))
             (inc pos))
      (do
        (set! (.-position state) pos)
        (persistent! bytes)))))

(defn take-byte! [state]
  (first (take-bytes! 1 state)))

(defn add-to-result! [exp state]
  (set! (.-result state) exp)
  state)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-small-integer [state]
  (-> state
      take-byte!
      (add-to-result! state)))

(defn decode-integer [state]
  (add-to-result! (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
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
  (let [length (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
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
  (let [length #?(:cljs (signed-int-from-4-bytes (apply array (take-bytes! 4 state))))]
    (add-to-result! (crypt/utf8ByteArrayToString (apply array (take-bytes! length state)))
                    state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode-new-float [state]
  (let [arr (new js/Uint8Array (apply array (take-bytes! 8 state)))
        buf (.-buffer arr)
        dv (new js/DataView buf)]
    (add-to-result! (.getFloat64 dv 0)
                    state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn common-atom [state]
  (let [length (unsigned-int-from-2-bytes (apply array (take-bytes! 2 state)))]
    (->> (take-bytes! length state)
         (apply array)
         crypt/utf8ByteArrayToString
         keyword)))

(defn decode-atom [state]
  (let [kw (common-atom state)]
    (cond
      (= :true kw) (add-to-result! true state)
      (= :false kw) (add-to-result! false state)
      :default (add-to-result! kw state))))

(defn decode-small-atom-utf8 [state]
  (let [length (take-byte! state)]
    (-> (apply array (take-bytes! length state))
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
  (let [length (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
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
  (let [length (unsigned-int-from-2-bytes (apply array (take-bytes! 2 state)))
        elements (take-bytes! length state)]

    (add-to-result! elements state)))

(defn decode-list [state]
  (let [length (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
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
                                (apply array)
                                signed-int-from-4-bytes)
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
