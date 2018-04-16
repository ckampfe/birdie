(ns birdie.decode
  #?(:cljs (:require [goog.crypt :as crypt])))

(defn make-state [bytes]
  (atom {:bytes bytes
         :result []}))

(def types
  {70  :NEW_FLOAT
   77  :BIT_BINARY
   82  :ATOM_CACHE_REFERENCE_INDEX
   97  :SMALL_INTEGER
   98  :INTEGER
   99  :FLOAT
   100 :ATOM ;; deprecated
   101 :REFERENCE
   102 :PORT
   103 :PID
   104 :SMALL_TUPLE
   105 :LARGE_TUPLE
   106 :NIL
   107 :STRING
   108 :LIST
   109 :BINARY
   110 :SMALL_BIG
   111 :LARGE_BIG
   112 :NEW_FUN
   113 :EXPORT ;; MFA
   114 :NEW_REFERENCE
   115 :SMALL_ATOM ;; deprecated
   116 :MAP
   117 :FUN
   118 :ATOM_UTF8
   119 :SMALL_ATOM_UTF8})

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

(ns-unmap 'birdie.decode 'do-decode)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn take-bytes! [n state]
  (let [bytes (take n (:bytes @state))]
    (swap! state
           (fn [s] (update s :bytes (fn [b] (drop n b)))))
    bytes))

(defn take-byte! [state]
  (first (take-bytes! 1 state)))

(defn add-to-result! [exp state]
  (swap! state
         (fn [s] (assoc s :result exp))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti do-decode (fn [state]
                      (let [identifier-byte #?(:cljs (take-byte! state))]
                        (get types identifier-byte))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod do-decode :SMALL_INTEGER [state]
  (-> state
      take-byte!
      (add-to-result! state)))

(defmethod do-decode :INTEGER [state]
  (add-to-result! (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
                  state))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod do-decode :SMALL_BIG [state]
  (let [length (take-byte! state)
        sign-byte (take-byte! state)
        digits (vec (take-bytes! length state))
        n (reduce (fn [acc val] (+ acc
                                   (* (get digits val)
                                      (.pow js/Math 256 val))))
                  0
                  (range length))]

    (add-to-result! (if (= 1 sign-byte) (- 0 n) n)
                    state)))

(defmethod do-decode :LARGE_BIG [state]
  (let [length (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
        sign-byte (take-byte! state)
        digits (vec (take-bytes! length state))
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

(defmethod do-decode :BINARY [state]
  (let [length #?(:cljs (signed-int-from-4-bytes (apply array (take-bytes! 4 state))))]
    (add-to-result! (crypt/utf8ByteArrayToString (apply array (take-bytes! length state)))
                    state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod do-decode :NEW_FLOAT [state]
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

(defmethod do-decode :SMALL_ATOM [state])

(defmethod do-decode :ATOM [state]
  (let [kw (common-atom state)]
    (cond
      (= :true kw) (add-to-result! true state)
      (= :false kw) (add-to-result! false state)
      :default (add-to-result! kw state))))

(defmethod do-decode :SMALL_ATOM_UTF8 [state]
  (let [length (take-byte! state)]
    (-> (apply array (take-bytes! length state))
        crypt/utf8ByteArrayToString
        keyword
        (add-to-result! state))))

(defmethod do-decode :ATOM_UTF8 [state]
  (add-to-result! (common-atom state)
                  state))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod do-decode :SMALL_TUPLE [state]
  (let [length (take-byte! state)
        elements (map (fn [_] (:result (do-decode state)))
                      (range length))]

    (add-to-result! (vec elements) state)))

(defmethod do-decode :LARGE_TUPLE [state]
  (let [length (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
        elements (map (fn [_] (:result (do-decode state)))
                      (range length))]

    (add-to-result! (vec elements) state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod do-decode :NIL [state]
  (add-to-result! [] state))

(defmethod do-decode :STRING [state]
  (let [length (unsigned-int-from-2-bytes (apply array (take-bytes! 2 state)))
        elements (take-bytes! length state)]

    (add-to-result! (vec elements) state)))

(defmethod do-decode :LIST [state]
  (let [length (signed-int-from-4-bytes (apply array (take-bytes! 4 state)))
        elements (mapv (fn [_] (:result (do-decode state)))
                       (range length))
        tail (:result (do-decode state))]

    ;; proper list has [] as tail, improper has anything else
    (add-to-result! (if (= [] tail)
                      (vec elements)
                      (conj (vec elements) tail))
                    state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod do-decode :MAP [state]
  (let [number-of-kv-pairs (->> state
                                (take-bytes! 4)
                                (apply array)
                                signed-int-from-4-bytes)
        elements (reduce (fn [acc val]
                           (assoc acc
                                  (:result (do-decode state))
                                  (:result (do-decode state))))
                         {}
                         (range number-of-kv-pairs))]

    (add-to-result! elements state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod do-decode :default [byte-array]
  (println "default")
  (println byte-array))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode [s]
  (let [state (make-state s)]
    (assert (= 131 (take-byte! state)))
    (do-decode state)
    (:result @state)))
