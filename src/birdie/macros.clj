(ns birdie.macros)

(defmacro int-to-2-bytes [n]
  `(let [buf# (new js/ArrayBuffer 2)
         byte-view# (new js/Uint8Array buf#)
         int-view# (new js/Int16Array buf#)]

     (aset int-view# 0 ~n)

     [(aget byte-view# 1)
      (aget byte-view# 0)]))

(defmacro int-to-4-bytes [n]
  `(let [buf# (new js/ArrayBuffer 4)
        byte-view# (new js/Uint8Array buf#)
        int-view# (new js/Int32Array buf#)]

    (aset int-view# 0 ~n)

    [(aget byte-view# 3)
     (aget byte-view# 2)
     (aget byte-view# 1)
     (aget byte-view# 0)]))

(defmacro double-to-8-bytes [n]
  `(let [buf# (new js/ArrayBuffer 8)
         byte-view# (new js/Uint8Array buf#)
         float-view# (new js/Float64Array buf#)]

     (aset float-view# 0 ~n)

     [(aget byte-view# 7)
      (aget byte-view# 6)
      (aget byte-view# 5)
      (aget byte-view# 4)
      (aget byte-view# 3)
      (aget byte-view# 2)
      (aget byte-view# 1)
      (aget byte-view# 0)]))
