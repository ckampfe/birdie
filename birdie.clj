;; gorilla-repl.fileformat = 1

;; **
;;; # birdie performance journey - Clark Kampfe
;; **

;; **
;;; (note: this post remains a work in progress)
;;; 
;;; Recently I began work on a library called [Birdie](https://github.com/ckampfe/birdie).
;;; 
;;; Birdie is an implementation to encode and decode [Erlang's External Term Format](http://erlang.org/doc/apps/erts/erl_ext_dist.html) in Clojurescript.
;;; 
;;; Erlang Term Format, or ETF, is a high performance binary protcol native to the Erlang VM. 
;;; With it, you can encode Erlang data - "terms" - to binary data, and deserialize valid binary data into Erlang terms.
;;; 
;;; If you're familiar with JSON, ETF is to Erlang as JSON is to Javascript. As an analogy, this isn't exactly right, but I use it to show that ETF has equivalent built-in support on the Erlang VM as JSON does with Javascript.
;;; 
;;; Birdie brings this to the client and NodeJS, by converting Clojurescript/Javascript expressions to and from ETF. Birdie is not the first library to do this in the browser, but I am not aware of another Clojurescript implementation.
;;; 
;;; It is not a full implementation, insofar as it does not provide decoders for every single addressable Erlang and Clojure type (for example, things like pids and ports have no Clojurescript/Javascript representation), but it is roughly equiavalent to JSON in expressable types.
;;; 
;;; My goals for Birdie are:
;;; 
;;; 1. Learn about ETF.
;;; 2. Produce a working, tested implementation. Maybe not production grade, but at minimum good enough for others to learn from and extend if they want to.
;;; 3. Learn how to optimize Clojurescript.
;;; 3. Reasonable performance. For me, this means within a few multiples of the optimized JSON parsing stacks in modern JS engines.
;; **

;; **
;;; ## Why binary? Why Birdie?
;; **

;; **
;;; Protocols exist because we like our applications to communicate with each other. In the course of solving problems, many systems - both human and machine - are dominated by their communications.
;;; 
;;; In computing, we talk about a few different kind of formats. Text protocols are those that are designed to enable machine communication while still being human-readable. Binary protocols do the same, but are not designed to be human readable.
;;; 
;;; The tradeoffs of text and binary formats are interesting.
;;; Textual formats are human-readable, but bulky and require a parse step.
;;; 
;;; Not designed to be human-readable, binary formats are, generally, more information-dense, and faster. They do not require a parse step per se, as the program can in many situations
;;; address blocks of bytes at a time rather dispatching stateful actions on the basis of one byte at a time.
;; **

;; **
;;; As an example of the tradeoffs, benchmarking ETF vs. JSON on the server with the great [Jason](https://github.com/michalmuskala/jason) library shows ETF way out ahead on both encoding and decoding.
;;; 
;;; 
;; **

;; **
;;; ## How does Birdie work?
;; **

;; **
;;; In order to build something fast, you have to understand it.
;;; 
;;; There are two things you can do with a protocol like ETF or JSON. Encoding and decoding. Encoding corresponds to `JSON.stringify` and decoding corresponds to `JSON.parse`.
;;; 
;;; Birdie exposes these two operations through `birdie.core/encode` and `birdie.core/decode`.
;;; 
;;; #### Encoding
;;; 
;;; Birdie defines a protocol, `Encodable`, with one method, `do-encode`. For those unfamiliar with Clojurescript, a protocol is one of Clojurescript's mechanisms for doing polymorphic dispatch. Specifically, type-based polymorphic dispatch.
;;; 
;;; That is to say any type - Clojurescript or Javascript - having an implementation of `Encodable` can be a member of an expression passed to `encode` and it will be encoded to ETF. We can (and in Birdie, do) call `do-encode` on arbitrary types, and Clojurescript takes care of calling the right implementation. Other languages may call this "dynamic dispatch" or "ad-hoc polymorphism".
;;; 
;;; The implementation of `Encodable` for Clojurescript keywords looks like:
;;; 
;;; ```clojure
;;; (extend-protocol Encodable
;;;   cljs.core/Keyword
;;;   (do-encode [this] (encode-keyword this))
;;; 
;;; ```
;;; 
;;; The actual implementation of `encode-keyword` is not super important,
;;; but it is a good occasion to talk about ETF.
;;; 
;;; ETF maps Erlang types like tuples, maps, ints, floats, and atoms to byte vectors.
;;; 
;;; The schemas for these byte vectors follow a high-level pattern. They are constructed of a version byte, tag byte, a number of length bytes, and then data bytes. There are exceptions but that's the general pattern.
;;; 
;;; For example, imagine we did make the function call `(do-encode :ok)`.
;;; The result of this call is a 5-byte vector, `(131 119 2 111 107)`, in order made up of a version byte, a tag byte, a length byte, and 2 data bytes. 
;;; 
;;; The version byte is `131`, saying which version of ETF we are working with.
;;; 
;;; `119` is the tag byte, which maps directly to a data type. In this case, `SMALL_ATOM_UTF8_EXT`, meaning our keyword has been mapped to the small UTF8 encoding of an Erlang atom. (You can find the whole doc [here](http://erlang.org/doc/apps/erts/erl_ext_dist.html))
;;; 
;;; The spec for `SMALL_ATOM_UTF8_EXT` says that the remainder of the encoding is 1 byte for the length of the atom, followed by that many UTF8 encoded bytes. In our case, the length byte is `2`, meaning there are two data bytes. The two data bytes `111` and `107` are the UTF8 byte encodings of `o` and `k`, respectively.
;;; 
;;; Putting it all together, it looks like this:
;;; 
;;; 
;;; ```
;;; ┌──────────┬──────────┬──────────┬──────────┬──────────┐
;;; │ version  │          │  length  │          │          │
;;; │   byte   │ tag byte │   byte   │data byte │data byte │
;;; │          │          │          │          │          │
;;; ├──────────┼──────────┼──────────┼──────────┼──────────┤
;;; │          │          │          │          │          │
;;; │   131    │   119    │    2     │   111    │   107    │
;;; │          │          │          │          │          │
;;; └──────────┴──────────┴──────────┴──────────┴──────────┘
;;; ```
;;; 
;;; 
;;; Calling `encode` with a complex type, like a Clojurescript vector, will find the `Encodable` implementation for `cljs.core/PersistentVector`, calling its `do-encode` method, which in turn calls `encode-seq`.
;;; 
;;; `encode-seq` will `reduce` over the Clojurescript vector, calling `do-encode` on every constituent item (a vector is equivalent to an array or list in other languages), building up a new vector comprising the combined ETF bytes representation of every item in the original vector.
;;; 
;;; When this is complete, `encode-seq` does three more things. First, it gets the length of the input vector and turns it into a 4-bytes big endian, concatenating these 4 bytes onto the front of the ETF byte vector. Then, it concats on the tag byte, which for Erlang lists is `108`. Then, it adds the version byte, `131`.
;;; 
;;; 
;;; #### Decoding
;;; 
;;; Decoding ETF into Clojurescript data is a bit more complicated, but not much.
;;; 
;;; The decoding process first creates some starting state. This state is comprised of three things: The source ETF byte vector, a result which is initially empty, and a cursor position, which is initially 0.
;;; 
;;; To decode the source ETF byte vector into Clojurescript forms, we walk the byte vector, making decisions as we go.
;;; 
;;; For example, if you recall the previous example of the encoded form of `:ok`, it is `(131 119 2 111 107)`.
;;; 
;;; The way we walk this vector is by keeping track of a cursor position. That is, an integer that tells us the current byte of the vector that we care about.
;;; 
;;; Before going any further, a trace of decoding `(131 119 2 111 107)` looks like this:
;;; 
;;; ```
;;; #birdie.decode.State{:bytes [131 119 2 111 107], :result [], :position 0}
;;; #birdie.decode.State{:bytes [131 119 2 111 107], :result [], :position 1}
;;; #birdie.decode.State{:bytes [131 119 2 111 107], :result [], :position 2}
;;; #birdie.decode.State{:bytes [131 119 2 111 107], :result [], :position 3}
;;; #birdie.decode.State{:bytes [131 119 2 111 107], :result [], :position 5}
;;; #birdie.decode.State{:bytes [131 119 2 111 107], :result :ok, :position 5}
;;; ```
;;; 
;;; So, if we read the position value as an indication of the work the programming is doing, it follows these steps:
;;; 
;;; 1. Look at the very first byte at index 0. Assert that it is `131` so we crash rather than reading byte data that is not ETF. Advance the cursor position to 1.
;;; 2. Read the second byte, the tag byte, at index 1. This byte is `119`. Using the `dispatch` function, figure out what to do when the byte is `119`. Advance the cursor to position 2.
;;; 3. The dispatch function says that we should call `decode-small-atom-utf8`, so do that.
;;; 4. The `decode-small-atom-utf8` takes one byte, from position 2. This byte is the length byte. This advances the cursor to position 3.
;;; 5. `decode-small-atom-utf8` then takes this number of bytes from the vector, starting at its cursor position. In this case, that means we are taking byte at position `3`, advancing the cursor by one to `4`, and taking that byte. Advancing to position 5 concludes the reading of bytes from the source byte vector, as 5 > 4, which is the last position of the vector.
;;; 6. At this point, we have an array of 2 bytes. We treat these two bytes as if they were UTF8 characters to get back a string.
;;; 7. We then turn this string into a keyword.
;;; 8. We set the `:result` value of our state to our keyword, `:ok`.
;;; 
;;; You can extrapolate this process to different tag bytes, different lengths, and different ways of reading/transforming the data bytes, but the above process is the gist of it.
;;; 
;;; For a functional language like Clojurescript, using a cursor like this to read a sequential structure at positions is unusual. Most of the time you would use things like `map` or `reduce`. Using a cursor like this is for performance, as it lets us avoid a ton of intermediate collections, which in turn lets us avoid significant garbage collection. You'll be able to see this in the benchmarks below.
;; **

;; **
;;; 
;; **

;; **
;;; # "Was he slow?"
;;; 
;;; There have been a few phases of my work on Birdie so far.
;;; 
;;; After getting a mostly working implementation, the decoding portion has seen so far 5 optimization phases. The encoding portion has seen two optimization phases.
;;; 
;;; ### Decoding
;;; 
;;; #### Phase 0
;;; 
;;; I first endeavored to make a correct implementation.
;;; 
;;; As I knew nothing of ETF's implementation, this involved lots of reading the docs and using
;;; the safest possible Clojurescript facilities, which are the normal immutable persistent collections and sequence operations.
;;; I set up the project, wrote simple implementations of encoding and decoding for the scalar types, and moved on to the composite types all while writing a few unit tests.
;;; 
;;; #### Phase 1
;;; 
;;; 
;;; Define state as a record instead of an atom of a map. I've seen various rumblings around the web about records having different access characteristics in Clojurescript than maps.
;;; 
;;; 
;;; Went from this:
;;; 
;;; ```
;;; (defn make-state [bytes]
;;;   (atom {:bytes bytes
;;;          :result []}))
;;;          
;;; (defn take-bytes! [n state]
;;;   (let [bytes (take n (:bytes @state))]
;;;     (swap! state
;;;            (fn [s] (update s :bytes (fn [b] (drop n b)))))
;;;     bytes))
;;; ```
;;; 
;;; 
;;; To this:
;;; 
;;; ```
;;; (defrecord State [bytes result])
;;; 
;;; (defn take-bytes! [n state]
;;;   (let [[bytes remaining] (split-at n (.-bytes state))]
;;;     (set! (.-bytes state) remaining)
;;;     bytes))
;;; ```
;;; 
;;; 
;;; #### Phase 2
;;; 
;;; Transients
;;; 
;;; Changed the internal element decoding in lists/maps from:
;;; 
;;; ```
;;; (mapv (fn [_] (.-result (do-decode state)))
;;;                        (range length))
;;; ```
;;; 
;;; to
;;; 
;;; ```
;;; (loop [i 0
;;;        c (transient [])]
;;;   (if (< i length)
;;;     (recur (inc i)
;;;            (conj! c (.-result (do-decode state))))
;;;     c))
;;; ```
;;; 
;;; 
;;; #### Phase 3
;;; 
;;; Constant time dispatch function
;;; 
;;; 
;;; Went from a typemap and multimethod-based dispatch like
;;; 
;;; ```
;;; (defmethod do-decode :ATOM_UTF8 [state]
;;;   (add-to-result! (common-atom state)
;;;                   state))
;;; 
;;; ```
;;; 
;;; to a `case` expression dispatching on the flag byte directly:
;;; 
;;; ```
;;; (defn dispatch [state]
;;;   (case (take-byte! state)
;;;     70 (decode-new-float state)
;;;     97 (decode-small-integer state)
;;;     98 (decode-integer state)
;;;     100 (decode-atom state)
;;;     104 (decode-small-tuple state)
;;;     105 (decode-large-tuple state)
;;;     106 (decode-nil state)
;;;     107 (decode-string state)
;;;     108 (decode-list state)
;;;     109 (decode-binary state)
;;;     110 (decode-small-big state)
;;;     111 (decode-large-big state)
;;;     116 (decode-map state)
;;;     118 (decode-atom-utf8 state)
;;;     119 (decode-small-atom-utf8 state)
;;;     (decode-default state)))
;;; ```
;;; 
;;; 
;;; #### Phase 4
;;; 
;;; Cursor-based byte consumption with transients
;;; 
;;; Before:
;;; 
;;; ```
;;; (defn take-bytes! [n state]
;;;   (let [[bytes remaining] (split-at n (.-bytes state))]
;;;     (set! (.-bytes state) remaining)
;;;     bytes))
;;; ```
;;; 
;;; After:
;;; 
;;; ```
;;; (defn take-bytes! [n state]
;;;   (loop [i 0
;;;          bytes (transient [])
;;;          pos (.-position state)]
;;;     (if (< i n)
;;;       (recur (inc i)
;;;              (conj! bytes (nth (.-bytes state) pos))
;;;              (inc pos))
;;;       (do
;;;         (set! (.-position state) pos)
;;;         (persistent! bytes)))))
;;; 
;;; ```
;;; 
;;; 
;;; #### Phase 5
;;; 
;;; Cursor-based byte consumption with mutable JS array; preallocate byte slices
;;; 
;;; Finally, we took the previous byte cursor implementation and changed it to this:
;;; 
;;; ```
;;; (defn take-bytes! [n state]
;;;   (let [bytes (make-array n)]
;;;     (loop [i 0
;;;            pos (.-position state)]
;;;       (if (< i n)
;;;         (do
;;;           (aset bytes i (nth (.-bytes state) pos))
;;;           (recur (inc i)
;;;                  (inc pos)))
;;;         (do
;;;           (set! (.-position state) pos)
;;;           bytes)))))
;;; ```
;;; 
;;; Note that `bytes` is now preallocated to the exact size that the caller has requested.
;;; Before, it would have to grow this collection with the addition of every element.
;;; Also observe that we are using `(make-array)`, which is a native Javascript array rather
;;; than a Clojurescript persistent or transient vector as before.
;;; 
;;; 
;;; #### Phase 6
;;; 
;;; Probably the most common operation in Birdie is to read a specific number of bytes as an integer. This is common because types in ETF are represented as a tag byte, some kind of length indicating how much more data to read, and then a corresponding number of data bytes.
;;; 
;;; For example, strings in ETF are encoded as the number 109 (a single byte), followed by a 4-byte unsigned integer representing the number of data bytes, followed by that number of data bytes.
;;; 
;;; This is part of what makes ETF and other binary protocols fast: the program is able to address data without having to necessarily decode it first, or to branch and determine whether it has found, for example, a closing quotation mark or a closing curly brace.
;;; 
;;; Birdie's implementation for this crucial functionality has until this phase looked like this:
;;; 
;;; ```
;;; #?(:cljs (defn unsigned-int-from-2-bytes [byte-array]
;;;            (let [arr (new js/Uint8Array byte-array)
;;;                  buf (.-buffer arr)
;;;                  dv (new js/DataView buf)]
;;; (.getUint16 dv 0))))
;;; ```
;;; 
;;; This bit of code takes some byte array, initializes a new appropriately sized typed array with from the given byte array, and reads the appropriate number of bytes as an unsigned 16 bit integer.
;;; 
;;; Note the instances of the word `new`. There are two new objects allocated every time we call this function. As this function and its 4 and 8 byte comrades are called for nearly every single term that is decoded, this represents a significant amount of allocation and garbage collection.
;;; 
;;; I've optimized this action for all numeric conversion functions to allocate memory for each conversion type at initialization time, and then to reuse that memory for every subsequent conversion. It looks like this:
;;; 
;;; ```
;;; (def TWO_BYTES #?(:cljs (new js/Uint8Array (new js/ArrayBuffer 2))))
;;; (def TWO_BYTE_DV #?(:cljs (new js/DataView (.-buffer TWO_BYTES))))
;;; 
;;; (defn unsigned-int-from-2-bytes [byte-array]
;;;   (aset TWO_BYTES 0 (aget byte-array 0))
;;;   (aset TWO_BYTES 1 (aget byte-array 1))
;;;   (.getUint16 TWO_BYTE_DV 0))
;;; ```
;;; 
;;; Now, we allocate a 2-byte typed array once, at the beginning of the program, and mutate it any time we need to convert 2 bytes into a 16-bit unsigned int. This optimization has had a huge effect on runtime.
;;; 
;;; #### Phase 7
;;; 
;;; This phase optimizes the all-important `take-bytes!` codepath by swapping the place in which the work of taking bytes is performed.
;;; Originally, this work was performed in a `loop` in `take-bytes!`, with `take-byte!` (singular) being a special case that calls `take-bytes!` with an argument of `1`.
;;; 
;;; ```
;;; (defn take-bytes! [n state]
;;;   (let [bytes (make-array n)]
;;;     (loop [i 0
;;;            pos (.-position state)]
;;;       (if (< i n)
;;;         (do
;;;           (aset bytes i (nth (.-bytes state) pos))
;;;           (recur (inc i)
;;;                  (inc pos)))
;;;         (do
;;;           (set! (.-position state) pos)
;;;           bytes)))))
;;;  
;;; (defn take-byte! [state]
;;;   (aget (take-bytes! 1 state)
;;;         0))
;;; ```
;;; 
;;; The change inverts this, to make `take-byte!` the main event, and `take-bytes!` (plural) a special case that calls `take-byte!` multiple times in a loop:
;;; 
;;; ```
;;;  (defn take-byte! [state]
;;;   (let [current-position (.-position state)]
;;;     (set! (.-position state)
;;;           (inc current-position))
;;;     (aget (.-bytes state) current-position)))
;;; 
;;; (defn take-bytes! [n state]
;;;   (let [bytes (make-array n)]
;;;     (dotimes [i n]
;;;       (aset bytes i (take-byte! state)))
;;;     bytes))
;;; ```
;;; 
;;; This has the effect of avoiding some setup/allocation for the `take-byte!` case, which is common, and only entering the loop if necessary for the case where we know we need to take more than one byte.
;;; 
;;; 
;;; ### Encoding
;;; 
;;; #### Phase 0
;;; 
;;; The initial functioning implementation for encoding Clojurescript expressions to ETF
;;; used multimethods, like this:
;;; 
;;; ```
;;; (defmethod encode cljs.core/PersistentHashMap [exp] (encode-map exp))
;;; ```
;;; 
;;; I reached for this method because it is expressive, intuitive, and extensible.
;;; It was easy to write tests piecemeal, and back into functionality even without having a fully-working implementation for every type.
;;; 
;;; #### Phase 1
;;; 
;;; So far the only optimization phase on the encoding side has been to swap out
;;; multimethod dispatch for protocol-based dispatch, like this:
;;; 
;;; ```
;;; (defprotocol Encodable
;;;   (do-encode [this]))
;;;   
;;; (extend-protocol Encodable
;;;   cljs.core/Keyword
;;;   (do-encode [this] (encode-keyword this)))
;;;   
;;;   ;; rest of implementation omitted
;;; ```
;;; 
;;; while also swapping out the inner loops in complex types for the `transient` based approach used on the decoding side.
;;; 
;;; #### Phase 2
;;; 
;;; Two different kinds of optimizations.
;;; First, removing `apply`, and swapping in `reduce`.
;;; 
;;; ```
;;; (apply conj!
;;;   (apply conj! acc (do-encode k))
;;;   (do-encode v)))
;;; ```
;;; 
;;; becomes
;;; 
;;; ```
;;; (reduce conj!
;;;   (reduce conj! acc (do-encode k))
;;;   (do-encode v)))
;;; ```
;;; 
;;; Second, using `into` instead of `concat` for concatenating length bytes and data bytes.
;;; 
;;; ```
;;; (cons 116 (concat length-bytes elements))
;;; ```
;;; 
;;; vs
;;; 
;;; ```
;;; (cons 116 (into length-bytes elements))
;;; ```
;;; 
;;; #### Phase 3
;;; 
;;; The third group of optimizations for encoding was to unroll byte conversion loops, typehint boolean functions, and use js arrays internally on strings.
;;; 
;;; The loop unrolling is a classic. Because we know how many bytes we're going to use in functions like `int-to-4-bytes`, we can avoid a loop access and hard code the index accesses. This avoids any intermediate results and the resulting GC.
;;; 
;;; Before:
;;; 
;;; ```
;;; (mapv (fn [i] (aget byte-view i))
;;;           (rseq (vec (range 4))))))
;;; ```
;;; 
;;; After:
;;; 
;;; ```
;;; [(aget byte-view 3)
;;;  (aget byte-view 2)
;;;  (aget byte-view 1)
;;;  (aget byte-view 0)]))
;;; ```
;;; 
;;; Typehinting booleans is something I took from Mike Fikes' great post here: http://blog.fikesfarm.com/posts/2015-12-04-boolean-type-hints-in-clojurescript.html
;;; 
;;; The optimization involves annotating your boolean functions like:
;;; 
;;; ```
;;; (defn ^boolean is-float? [n]
;;;   (not= (js/parseInt n 10) n))
;;; ```
;;; 
;;; This helps the Clojurescript compiler emit tighter Javascript that does not need to check for 0, `""`, or other "falsey" or "truthy" values.
;;; 
;;; The final optimization in this round changed `encode-string` to internally mutate a js array in an unrolled loop rather than use `reduce` with Clojurescript transients. It looks basically like the loop unroll above. If you wish to see the code you can go [here](https://github.com/ckampfe/birdie/commit/a3c3b3c8ea193146d735d3c08fa75497567c5fa5#diff-208b0ce56d6ed942ade7df36214bbdd1L77).
;;; 
;;; 
;;; #### Phase 4
;;; 
;;; The fourth phase of encoding optimizations has been to convert the encode pipeline to use js arrays internally for everything, and mutating those arrays when possible.
;;; 
;;; The commit is [here](https://github.com/ckampfe/birdie/commit/f1460cc305f413fc7b44bede103ae7e3bd168ac6). As it turns out, using arrays like this over persistent collections is a big performance pickup for hot-path code. I don't see this as something most people would want to do in most Clojurescript projects, as the productivity benefits of the persistent collections are huge. However, for low-level code like protocols or signal processing, it's great that native host arrays are available with no fuss.
;;; 
;;; For Birdie, this has resulted in a speedup that puts encoding performance ahead of JSON.
;;; 
;;; This phase let me bump up against the Javascript `Array` API, which is a pretty weird combination of functions that mutate their receiver and functions that return new arrays. For example, `push`, `unshift`, etc., mutate their receiver, while calling `concat` like `a.concat(b)` will produce a new array `c` that is the concatenation of `a` and `b`, letting `a` and `b` fall out of scope. I wanted to try to avoid this extra allocation so I searched around and found a pretty bizarre trick that appears to be working.
;;; 
;;; It turns out that you can use the mutable `Array` methods like `push` and `unshift` in combination with `apply` do a mutable version of `concat`, like this: `a.push.apply(a, b)`. This mutates `a` by concatentating `b` onto it, in place. I bet it reallocates space under the hood, but it was useful to not have to explicitly use a new array and GC the old ones.
;;; 
;;; In Clojurescript it looks like this:
;;; 
;;; ```
;;; (.apply (.-push length-bytes)
;;;         length-bytes
;;;         (do-encode el))
;;; ```
;;; 
;;; Note that we are accessing `push` as a field on the receiver, and then calling `apply` on the result.
;;; 
;;; #### Phase 5
;;; 
;;; This phase of encoding optimization is largely the same as Phase 6 of decoding optimization.
;;; It uses the exact same strategy of preallocating and then reusing fixed-size typed arrays for numeric conversions:
;;; 
;;; ```
;;; (def TWO_BYTES #?(:cljs (new js/ArrayBuffer 2)))
;;; (def TWO_BYTE_VIEW #?(:cljs (new js/Uint8Array TWO_BYTES)))
;;; (def TWO_BYTES_AS_INT16 #?(:cljs (new js/Int16Array TWO_BYTES)))
;;; 
;;; (defn int-to-2-bytes [n]
;;;   (aset TWO_BYTES_AS_INT16 0 n)
;;;   (array
;;;    (aget TWO_BYTE_VIEW 1)
;;;    (aget TWO_BYTE_VIEW 0)))
;;; ```
;;; 
;;; This optimization has had an equally dramatic effect on runtime as it did on the decode side.
;;; 
;;; #### Phase 6
;;; 
;;; I read [this great post](http://yellerapp.com/posts/2014-05-21-tuning-clojure-an-experience-report.html) about Clojure performance tuning recently, and it gave me the idea to try to use `reduce-kv` for encoding maps.
;;; 
;;; Previously, the encoding code path for maps looked like this:
;;; 
;;; ```
;;; (defn encode-map [exp]
;;;  (let [length-bytes (->> exp
;;;                            count
;;;                            int-to-4-bytes)]
;;; 
;;;  (doseq [[k v] exp]
;;;       (.apply (.-push length-bytes)
;;;               length-bytes (do-encode k))
;;;       (.apply (.-push length-bytes)
;;;               length-bytes (do-encode v)))
;;;    (.unshift length-bytes 116)
;;;      length-bytes))
;;; ```
;;; 
;;; Note how the `doseq` loop walks through `exp`, which is a map, destructuring each map entry individually into a `[k v]` vector, to perform work on each `k` and `v`. This is fine, and it works, but it turns out that `reduce-kv` is about 20% faster for the same operation. The code barely changes:
;;; 
;;; ```
;;; (defn encode-map [exp]
;;;   (let [length-bytes (->> exp
;;;                           count
;;;                           int-to-4-bytes)]
;;;     (reduce-kv (fn [_acc k v]
;;;                  (.apply (.-push length-bytes)
;;;                          length-bytes (do-encode k))
;;;                  (.apply (.-push length-bytes)
;;;                          length-bytes (do-encode v))
;;;                  _acc)
;;;                :ok
;;;                exp)
;;; 
;;;     (.unshift length-bytes 116)
;;;     length-bytes))
;;; ```
;; **

;; **
;;; ## Benchmark methodology
;;; 
;;; I ran the benchmarks using the great [Lumo](https://github.com/anmonteiro/lumo) Clojurescript runtime, which uses NodeJS. Being on OSX, I could use [Planck](http://planck-repl.org/), which uses JavaScriptCore instead, but I felt Node represented a decent, consistent runtime across a multiple different platforms that approximated Chrome. Next steps would be to build a harness to run these benchmarks in major browsers, but Lumo was close at hand and let me get good ballpark figures quickly.
;;; 
;;; My machine is a 2015 i7 Macbook Pro, and I closed all resource intensive stuff like Firefox, Slack, Spotify, etc., before running the benchmarks. While even a 3 year old i7 Macbook Pro is likely more powerful than most consumer hardware out there today, it represents a good projection of where most phones and laptops will be in a year or two, if they are not already there. It is my hope that should you try to replicate the results that follow, you will not get the exact same numbers, but that you would at least get similar differences between the optimization phases.
;;; 
;;; The [benchmarks](https://github.com/ckampfe/birdie/blob/master/decode-bench.cljs) themselves
;;; are not elaborate.
;;; 
;;; They use the built-in `simple-benchmark` functionality, which lets you time how long it takes to run a bit of code a specified number of times.
;;; 
;;; You can check the benchmark source for more detail, but each benchmark involves encoding and decoding a specific piece of data a number of times in a tight loop, as fast as it can.
;;; 
;;; For decoding benchmarks, each individual benchmark is run both with `birdie.core/decode` as well as `JSON.parse`.
;;; 
;;; For encoding bencharmks, each individual bencharmk is run both with `birdie.core/encode` as well as `JSON.stringify`.
;;; 
;;; The example data follows.
;;; 
;;; - a small vector of integers
;;; - a small vector strings
;;; - a large vector of integers
;;; - a large heterogenous vector composed of a few different datatypes
;;; - a small map
;;; - a large map
;;; 
;;; `small` data is run 10,000 times, and `large` data is run 100 times.
;;; 
;;; Individually, each benchmark is run 8 times in a row. The first five runs are dry runs in order to allow Node to cache/JIT the application code and achieve a steady state. The results of those 5 runs are not recorded. The final three runs each record their runtime in milliseconds. For this post, I chose the lowest of the 3 reported runs to show how fast the code could be expected to go in a steady state.
;;; 
;;; It's valid to question why I didn't choose the median or mean of the three runs, but I can tell you that on my hardware there was little meainingful variance between any of the three runs for any of the 6 benchmarks on any of the 6 optimization phases. Node reached a predictable state with few outliers, as nothing else was competing for resources either in the process itself or on the OS. The raw data is committed in each branch if you are curious.
;;; 
;;; 
;; **

;; @@
(ns birdie-performance.core
  (:require [gorilla-plot.core :as plot]
            [gorilla-repl.table :as table]
            [gorilla-repl.html :as html]))
;; @@
;; =>
;;; {"type":"html","content":"<span class='clj-nil'>nil</span>","value":"nil"}
;; <=

;; @@
(def decode-data
  {"small vector integers; 10,000 iterations"  [296 231 214 157 88 40 9 6 197]
   "small vector strings; 10,000 iterations"   [598 484 491 450 253 144 40 21 49]
   "large vector integers; 100 iterations"     [6870 5450 5334 4743 2806 1558 357 74 3410]
   "large heterogenous vector; 100 iterations" [11449 9077 9115 6994 3981 2082 1002 657 2769]
   "small map; 10,000 iterations"              [672 507 553 463 257 121 50 26 155]
   "large map; 100 iterations"                 [8141 6485 6495 5560 3339 1595 697 386 2085]
           })
;; @@
;; =>
;;; {"type":"html","content":"<span class='clj-var'>#&#x27;birdie-performance.core/decode-data</span>","value":"#'birdie-performance.core/decode-data"}
;; <=

;; @@
(defn plot-decode-benchmark [[name data-in-order]]
  [name (plot/bar-chart
          [:unoptimized
           :one
           :two
           :three
           :four
           :five
           :six
           :seven
           :json]
          data-in-order)
        (table/table-view (into [] (zipmap
                                     [:unoptimized
                                      :one
                                      :two
                                      :three
                                      :four
                                      :five
                                      :six
                                      :seven
                                      :json]
                                     data-in-order ))
                          :columns ["phase" "runtime (milliseconds)"]) 
   ])
;; @@
;; =>
;;; {"type":"html","content":"<span class='clj-var'>#&#x27;birdie-performance.core/plot-decode-benchmark</span>","value":"#'birdie-performance.core/plot-decode-benchmark"}
;; <=

;; **
;;; ## Decoding benchmarks
;;; 
;;; Things to note:
;;; 
;;; - The legend, describing the optimization phases
;;; - The rightmost bar in the bar graph is the time for native JSON decoding using `JSON.parse`
;; **

;; @@
(table/table-view
  (concat [["legend" (table/table-view [['unoptimized "none"]
                                        ['one "state as record"]
                                        ['two "transients"]
                                        ['three "constant time dispatch"]
                                        ['four "cursor"]
                                        ['five "cursor, native js array, preallocate slices"]
                                        ['six "reuse numeric conversion arrays"
                                         'seven "optimize take-bytes codepath"]]
                                       :columns ['phase 'optimizations])]]
  (map plot-decode-benchmark decode-data)
          
          )
  :columns [(html/html-view "Benchmark") (html/html-view "Plot")])
;; @@
;; =>
;;; {"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"Benchmark","value":"#gorilla_repl.html.HtmlView{:content \"Benchmark\"}"},{"type":"html","content":"Plot","value":"#gorilla_repl.html.HtmlView{:content \"Plot\"}"}],"value":"[#gorilla_repl.html.HtmlView{:content \"Benchmark\"} #gorilla_repl.html.HtmlView{:content \"Plot\"}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;legend&quot;</span>","value":"\"legend\""},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-symbol'>phase</span>","value":"phase"},{"type":"html","content":"<span class='clj-symbol'>optimizations</span>","value":"optimizations"}],"value":"[phase optimizations]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>unoptimized</span>","value":"unoptimized"},{"type":"html","content":"<span class='clj-string'>&quot;none&quot;</span>","value":"\"none\""}],"value":"[unoptimized \"none\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>one</span>","value":"one"},{"type":"html","content":"<span class='clj-string'>&quot;state as record&quot;</span>","value":"\"state as record\""}],"value":"[one \"state as record\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>two</span>","value":"two"},{"type":"html","content":"<span class='clj-string'>&quot;transients&quot;</span>","value":"\"transients\""}],"value":"[two \"transients\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>three</span>","value":"three"},{"type":"html","content":"<span class='clj-string'>&quot;constant time dispatch&quot;</span>","value":"\"constant time dispatch\""}],"value":"[three \"constant time dispatch\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>four</span>","value":"four"},{"type":"html","content":"<span class='clj-string'>&quot;cursor&quot;</span>","value":"\"cursor\""}],"value":"[four \"cursor\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>five</span>","value":"five"},{"type":"html","content":"<span class='clj-string'>&quot;cursor, native js array, preallocate slices&quot;</span>","value":"\"cursor, native js array, preallocate slices\""}],"value":"[five \"cursor, native js array, preallocate slices\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>six</span>","value":"six"},{"type":"html","content":"<span class='clj-string'>&quot;reuse numeric conversion arrays&quot;</span>","value":"\"reuse numeric conversion arrays\""},{"type":"html","content":"<span class='clj-symbol'>seven</span>","value":"seven"},{"type":"html","content":"<span class='clj-string'>&quot;optimize take-bytes codepath&quot;</span>","value":"\"optimize take-bytes codepath\""}],"value":"[six \"reuse numeric conversion arrays\" seven \"optimize take-bytes codepath\"]"}],"value":"#gorilla_repl.table.TableView{:contents [[unoptimized \"none\"] [one \"state as record\"] [two \"transients\"] [three \"constant time dispatch\"] [four \"cursor\"] [five \"cursor, native js array, preallocate slices\"] [six \"reuse numeric conversion arrays\" seven \"optimize take-bytes codepath\"]], :opts (:columns [phase optimizations])}"}],"value":"[\"legend\" #gorilla_repl.table.TableView{:contents [[unoptimized \"none\"] [one \"state as record\"] [two \"transients\"] [three \"constant time dispatch\"] [four \"cursor\"] [five \"cursor, native js array, preallocate slices\"] [six \"reuse numeric conversion arrays\" seven \"optimize take-bytes codepath\"]], :opts (:columns [phase optimizations])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;small vector integers; 10,000 iterations&quot;</span>","value":"\"small vector integers; 10,000 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"f4add6ea-40d4-4953-8858-88db9bc505f4","values":[{"x":"unoptimized","y":296},{"x":"one","y":231},{"x":"two","y":214},{"x":"three","y":157},{"x":"four","y":88},{"x":"five","y":40},{"x":"six","y":9},{"x":"seven","y":6},{"x":"json","y":197}]}],"marks":[{"type":"rect","from":{"data":"f4add6ea-40d4-4953-8858-88db9bc505f4"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"f4add6ea-40d4-4953-8858-88db9bc505f4","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"f4add6ea-40d4-4953-8858-88db9bc505f4","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :values ({:x :unoptimized, :y 296} {:x :one, :y 231} {:x :two, :y 214} {:x :three, :y 157} {:x :four, :y 88} {:x :five, :y 40} {:x :six, :y 9} {:x :seven, :y 6} {:x :json, :y 197})}], :marks [{:type \"rect\", :from {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>296</span>","value":"296"}],"value":"[:unoptimized 296]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>231</span>","value":"231"}],"value":"[:one 231]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>214</span>","value":"214"}],"value":"[:two 214]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>157</span>","value":"157"}],"value":"[:three 157]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>88</span>","value":"88"}],"value":"[:four 88]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>40</span>","value":"40"}],"value":"[:five 40]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:six</span>","value":":six"},{"type":"html","content":"<span class='clj-long'>9</span>","value":"9"}],"value":"[:six 9]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:seven</span>","value":":seven"},{"type":"html","content":"<span class='clj-long'>6</span>","value":"6"}],"value":"[:seven 6]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>197</span>","value":"197"}],"value":"[:json 197]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 296] [:one 231] [:two 214] [:three 157] [:four 88] [:five 40] [:six 9] [:seven 6] [:json 197]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"small vector integers; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :values ({:x :unoptimized, :y 296} {:x :one, :y 231} {:x :two, :y 214} {:x :three, :y 157} {:x :four, :y 88} {:x :five, :y 40} {:x :six, :y 9} {:x :seven, :y 6} {:x :json, :y 197})}], :marks [{:type \"rect\", :from {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 296] [:one 231] [:two 214] [:three 157] [:four 88] [:five 40] [:six 9] [:seven 6] [:json 197]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;small vector strings; 10,000 iterations&quot;</span>","value":"\"small vector strings; 10,000 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"8d330c07-0204-4a0c-b4a6-28952fb3461e","values":[{"x":"unoptimized","y":598},{"x":"one","y":484},{"x":"two","y":491},{"x":"three","y":450},{"x":"four","y":253},{"x":"five","y":144},{"x":"six","y":40},{"x":"seven","y":21},{"x":"json","y":49}]}],"marks":[{"type":"rect","from":{"data":"8d330c07-0204-4a0c-b4a6-28952fb3461e"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"8d330c07-0204-4a0c-b4a6-28952fb3461e","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"8d330c07-0204-4a0c-b4a6-28952fb3461e","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :values ({:x :unoptimized, :y 598} {:x :one, :y 484} {:x :two, :y 491} {:x :three, :y 450} {:x :four, :y 253} {:x :five, :y 144} {:x :six, :y 40} {:x :seven, :y 21} {:x :json, :y 49})}], :marks [{:type \"rect\", :from {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>598</span>","value":"598"}],"value":"[:unoptimized 598]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>484</span>","value":"484"}],"value":"[:one 484]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>491</span>","value":"491"}],"value":"[:two 491]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>450</span>","value":"450"}],"value":"[:three 450]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>253</span>","value":"253"}],"value":"[:four 253]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>144</span>","value":"144"}],"value":"[:five 144]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:six</span>","value":":six"},{"type":"html","content":"<span class='clj-long'>40</span>","value":"40"}],"value":"[:six 40]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:seven</span>","value":":seven"},{"type":"html","content":"<span class='clj-long'>21</span>","value":"21"}],"value":"[:seven 21]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>49</span>","value":"49"}],"value":"[:json 49]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 598] [:one 484] [:two 491] [:three 450] [:four 253] [:five 144] [:six 40] [:seven 21] [:json 49]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"small vector strings; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :values ({:x :unoptimized, :y 598} {:x :one, :y 484} {:x :two, :y 491} {:x :three, :y 450} {:x :four, :y 253} {:x :five, :y 144} {:x :six, :y 40} {:x :seven, :y 21} {:x :json, :y 49})}], :marks [{:type \"rect\", :from {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 598] [:one 484] [:two 491] [:three 450] [:four 253] [:five 144] [:six 40] [:seven 21] [:json 49]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;large vector integers; 100 iterations&quot;</span>","value":"\"large vector integers; 100 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"12ca4de5-89e3-4f6a-ba22-9c85d06dc382","values":[{"x":"unoptimized","y":6870},{"x":"one","y":5450},{"x":"two","y":5334},{"x":"three","y":4743},{"x":"four","y":2806},{"x":"five","y":1558},{"x":"six","y":357},{"x":"seven","y":74},{"x":"json","y":3410}]}],"marks":[{"type":"rect","from":{"data":"12ca4de5-89e3-4f6a-ba22-9c85d06dc382"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"12ca4de5-89e3-4f6a-ba22-9c85d06dc382","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"12ca4de5-89e3-4f6a-ba22-9c85d06dc382","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :values ({:x :unoptimized, :y 6870} {:x :one, :y 5450} {:x :two, :y 5334} {:x :three, :y 4743} {:x :four, :y 2806} {:x :five, :y 1558} {:x :six, :y 357} {:x :seven, :y 74} {:x :json, :y 3410})}], :marks [{:type \"rect\", :from {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>6870</span>","value":"6870"}],"value":"[:unoptimized 6870]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>5450</span>","value":"5450"}],"value":"[:one 5450]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>5334</span>","value":"5334"}],"value":"[:two 5334]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>4743</span>","value":"4743"}],"value":"[:three 4743]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>2806</span>","value":"2806"}],"value":"[:four 2806]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>1558</span>","value":"1558"}],"value":"[:five 1558]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:six</span>","value":":six"},{"type":"html","content":"<span class='clj-long'>357</span>","value":"357"}],"value":"[:six 357]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:seven</span>","value":":seven"},{"type":"html","content":"<span class='clj-long'>74</span>","value":"74"}],"value":"[:seven 74]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>3410</span>","value":"3410"}],"value":"[:json 3410]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 6870] [:one 5450] [:two 5334] [:three 4743] [:four 2806] [:five 1558] [:six 357] [:seven 74] [:json 3410]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"large vector integers; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :values ({:x :unoptimized, :y 6870} {:x :one, :y 5450} {:x :two, :y 5334} {:x :three, :y 4743} {:x :four, :y 2806} {:x :five, :y 1558} {:x :six, :y 357} {:x :seven, :y 74} {:x :json, :y 3410})}], :marks [{:type \"rect\", :from {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 6870] [:one 5450] [:two 5334] [:three 4743] [:four 2806] [:five 1558] [:six 357] [:seven 74] [:json 3410]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;large heterogenous vector; 100 iterations&quot;</span>","value":"\"large heterogenous vector; 100 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a","values":[{"x":"unoptimized","y":11449},{"x":"one","y":9077},{"x":"two","y":9115},{"x":"three","y":6994},{"x":"four","y":3981},{"x":"five","y":2082},{"x":"six","y":1002},{"x":"seven","y":657},{"x":"json","y":2769}]}],"marks":[{"type":"rect","from":{"data":"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :values ({:x :unoptimized, :y 11449} {:x :one, :y 9077} {:x :two, :y 9115} {:x :three, :y 6994} {:x :four, :y 3981} {:x :five, :y 2082} {:x :six, :y 1002} {:x :seven, :y 657} {:x :json, :y 2769})}], :marks [{:type \"rect\", :from {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>11449</span>","value":"11449"}],"value":"[:unoptimized 11449]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>9077</span>","value":"9077"}],"value":"[:one 9077]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>9115</span>","value":"9115"}],"value":"[:two 9115]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>6994</span>","value":"6994"}],"value":"[:three 6994]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>3981</span>","value":"3981"}],"value":"[:four 3981]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>2082</span>","value":"2082"}],"value":"[:five 2082]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:six</span>","value":":six"},{"type":"html","content":"<span class='clj-long'>1002</span>","value":"1002"}],"value":"[:six 1002]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:seven</span>","value":":seven"},{"type":"html","content":"<span class='clj-long'>657</span>","value":"657"}],"value":"[:seven 657]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>2769</span>","value":"2769"}],"value":"[:json 2769]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 11449] [:one 9077] [:two 9115] [:three 6994] [:four 3981] [:five 2082] [:six 1002] [:seven 657] [:json 2769]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"large heterogenous vector; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :values ({:x :unoptimized, :y 11449} {:x :one, :y 9077} {:x :two, :y 9115} {:x :three, :y 6994} {:x :four, :y 3981} {:x :five, :y 2082} {:x :six, :y 1002} {:x :seven, :y 657} {:x :json, :y 2769})}], :marks [{:type \"rect\", :from {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 11449] [:one 9077] [:two 9115] [:three 6994] [:four 3981] [:five 2082] [:six 1002] [:seven 657] [:json 2769]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;small map; 10,000 iterations&quot;</span>","value":"\"small map; 10,000 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"56c086ed-ee23-4555-ba9c-96061496fe7c","values":[{"x":"unoptimized","y":672},{"x":"one","y":507},{"x":"two","y":553},{"x":"three","y":463},{"x":"four","y":257},{"x":"five","y":121},{"x":"six","y":50},{"x":"seven","y":26},{"x":"json","y":155}]}],"marks":[{"type":"rect","from":{"data":"56c086ed-ee23-4555-ba9c-96061496fe7c"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"56c086ed-ee23-4555-ba9c-96061496fe7c","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"56c086ed-ee23-4555-ba9c-96061496fe7c","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :values ({:x :unoptimized, :y 672} {:x :one, :y 507} {:x :two, :y 553} {:x :three, :y 463} {:x :four, :y 257} {:x :five, :y 121} {:x :six, :y 50} {:x :seven, :y 26} {:x :json, :y 155})}], :marks [{:type \"rect\", :from {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>672</span>","value":"672"}],"value":"[:unoptimized 672]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>507</span>","value":"507"}],"value":"[:one 507]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>553</span>","value":"553"}],"value":"[:two 553]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>463</span>","value":"463"}],"value":"[:three 463]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>257</span>","value":"257"}],"value":"[:four 257]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>121</span>","value":"121"}],"value":"[:five 121]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:six</span>","value":":six"},{"type":"html","content":"<span class='clj-long'>50</span>","value":"50"}],"value":"[:six 50]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:seven</span>","value":":seven"},{"type":"html","content":"<span class='clj-long'>26</span>","value":"26"}],"value":"[:seven 26]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>155</span>","value":"155"}],"value":"[:json 155]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 672] [:one 507] [:two 553] [:three 463] [:four 257] [:five 121] [:six 50] [:seven 26] [:json 155]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"small map; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :values ({:x :unoptimized, :y 672} {:x :one, :y 507} {:x :two, :y 553} {:x :three, :y 463} {:x :four, :y 257} {:x :five, :y 121} {:x :six, :y 50} {:x :seven, :y 26} {:x :json, :y 155})}], :marks [{:type \"rect\", :from {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 672] [:one 507] [:two 553] [:three 463] [:four 257] [:five 121] [:six 50] [:seven 26] [:json 155]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;large map; 100 iterations&quot;</span>","value":"\"large map; 100 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"4adac382-5b40-4317-906b-be76d65a65ec","values":[{"x":"unoptimized","y":8141},{"x":"one","y":6485},{"x":"two","y":6495},{"x":"three","y":5560},{"x":"four","y":3339},{"x":"five","y":1595},{"x":"six","y":697},{"x":"seven","y":386},{"x":"json","y":2085}]}],"marks":[{"type":"rect","from":{"data":"4adac382-5b40-4317-906b-be76d65a65ec"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"4adac382-5b40-4317-906b-be76d65a65ec","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"4adac382-5b40-4317-906b-be76d65a65ec","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"4adac382-5b40-4317-906b-be76d65a65ec\", :values ({:x :unoptimized, :y 8141} {:x :one, :y 6485} {:x :two, :y 6495} {:x :three, :y 5560} {:x :four, :y 3339} {:x :five, :y 1595} {:x :six, :y 697} {:x :seven, :y 386} {:x :json, :y 2085})}], :marks [{:type \"rect\", :from {:data \"4adac382-5b40-4317-906b-be76d65a65ec\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"4adac382-5b40-4317-906b-be76d65a65ec\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"4adac382-5b40-4317-906b-be76d65a65ec\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>8141</span>","value":"8141"}],"value":"[:unoptimized 8141]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>6485</span>","value":"6485"}],"value":"[:one 6485]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>6495</span>","value":"6495"}],"value":"[:two 6495]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>5560</span>","value":"5560"}],"value":"[:three 5560]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>3339</span>","value":"3339"}],"value":"[:four 3339]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>1595</span>","value":"1595"}],"value":"[:five 1595]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:six</span>","value":":six"},{"type":"html","content":"<span class='clj-long'>697</span>","value":"697"}],"value":"[:six 697]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:seven</span>","value":":seven"},{"type":"html","content":"<span class='clj-long'>386</span>","value":"386"}],"value":"[:seven 386]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>2085</span>","value":"2085"}],"value":"[:json 2085]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 8141] [:one 6485] [:two 6495] [:three 5560] [:four 3339] [:five 1595] [:six 697] [:seven 386] [:json 2085]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"large map; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"4adac382-5b40-4317-906b-be76d65a65ec\", :values ({:x :unoptimized, :y 8141} {:x :one, :y 6485} {:x :two, :y 6495} {:x :three, :y 5560} {:x :four, :y 3339} {:x :five, :y 1595} {:x :six, :y 697} {:x :seven, :y 386} {:x :json, :y 2085})}], :marks [{:type \"rect\", :from {:data \"4adac382-5b40-4317-906b-be76d65a65ec\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"4adac382-5b40-4317-906b-be76d65a65ec\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"4adac382-5b40-4317-906b-be76d65a65ec\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 8141] [:one 6485] [:two 6495] [:three 5560] [:four 3339] [:five 1595] [:six 697] [:seven 386] [:json 2085]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"}],"value":"#gorilla_repl.table.TableView{:contents ([\"legend\" #gorilla_repl.table.TableView{:contents [[unoptimized \"none\"] [one \"state as record\"] [two \"transients\"] [three \"constant time dispatch\"] [four \"cursor\"] [five \"cursor, native js array, preallocate slices\"] [six \"reuse numeric conversion arrays\" seven \"optimize take-bytes codepath\"]], :opts (:columns [phase optimizations])}] [\"small vector integers; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :values ({:x :unoptimized, :y 296} {:x :one, :y 231} {:x :two, :y 214} {:x :three, :y 157} {:x :four, :y 88} {:x :five, :y 40} {:x :six, :y 9} {:x :seven, :y 6} {:x :json, :y 197})}], :marks [{:type \"rect\", :from {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"f4add6ea-40d4-4953-8858-88db9bc505f4\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 296] [:one 231] [:two 214] [:three 157] [:four 88] [:five 40] [:six 9] [:seven 6] [:json 197]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"small vector strings; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :values ({:x :unoptimized, :y 598} {:x :one, :y 484} {:x :two, :y 491} {:x :three, :y 450} {:x :four, :y 253} {:x :five, :y 144} {:x :six, :y 40} {:x :seven, :y 21} {:x :json, :y 49})}], :marks [{:type \"rect\", :from {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"8d330c07-0204-4a0c-b4a6-28952fb3461e\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 598] [:one 484] [:two 491] [:three 450] [:four 253] [:five 144] [:six 40] [:seven 21] [:json 49]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"large vector integers; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :values ({:x :unoptimized, :y 6870} {:x :one, :y 5450} {:x :two, :y 5334} {:x :three, :y 4743} {:x :four, :y 2806} {:x :five, :y 1558} {:x :six, :y 357} {:x :seven, :y 74} {:x :json, :y 3410})}], :marks [{:type \"rect\", :from {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"12ca4de5-89e3-4f6a-ba22-9c85d06dc382\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 6870] [:one 5450] [:two 5334] [:three 4743] [:four 2806] [:five 1558] [:six 357] [:seven 74] [:json 3410]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"large heterogenous vector; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :values ({:x :unoptimized, :y 11449} {:x :one, :y 9077} {:x :two, :y 9115} {:x :three, :y 6994} {:x :four, :y 3981} {:x :five, :y 2082} {:x :six, :y 1002} {:x :seven, :y 657} {:x :json, :y 2769})}], :marks [{:type \"rect\", :from {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"27ed2789-e8fa-4ad8-8cd6-f20724b7e83a\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 11449] [:one 9077] [:two 9115] [:three 6994] [:four 3981] [:five 2082] [:six 1002] [:seven 657] [:json 2769]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"small map; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :values ({:x :unoptimized, :y 672} {:x :one, :y 507} {:x :two, :y 553} {:x :three, :y 463} {:x :four, :y 257} {:x :five, :y 121} {:x :six, :y 50} {:x :seven, :y 26} {:x :json, :y 155})}], :marks [{:type \"rect\", :from {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"56c086ed-ee23-4555-ba9c-96061496fe7c\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 672] [:one 507] [:two 553] [:three 463] [:four 257] [:five 121] [:six 50] [:seven 26] [:json 155]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"large map; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"4adac382-5b40-4317-906b-be76d65a65ec\", :values ({:x :unoptimized, :y 8141} {:x :one, :y 6485} {:x :two, :y 6495} {:x :three, :y 5560} {:x :four, :y 3339} {:x :five, :y 1595} {:x :six, :y 697} {:x :seven, :y 386} {:x :json, :y 2085})}], :marks [{:type \"rect\", :from {:data \"4adac382-5b40-4317-906b-be76d65a65ec\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"4adac382-5b40-4317-906b-be76d65a65ec\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"4adac382-5b40-4317-906b-be76d65a65ec\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 8141] [:one 6485] [:two 6495] [:three 5560] [:four 3339] [:five 1595] [:six 697] [:seven 386] [:json 2085]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]), :opts (:columns [#gorilla_repl.html.HtmlView{:content \"Benchmark\"} #gorilla_repl.html.HtmlView{:content \"Plot\"}])}"}
;; <=

;; @@
(def encode-data 
  {"small vector integers; 10,000 iterations"  [219 129 64 51 31 20 20 196]
   "small vector strings; 10,000 iterations"   [563 374 297 187 102 56 58 36]
   "large vector integers; 100 iterations"     [8140 3580 3427 2097 1273 541 536 3042]
   "large heterogenous vector; 100 iterations" [10534 5683 4433 3287 1488 965 949 2590]
   "small map; 10,000 iterations"              [418 261 242 176 104 75 66 145]
   "large map; 100 iterations"                 [7335 3155 3197 2337 1468 1027 840 2058]
           })
;; @@
;; =>
;;; {"type":"html","content":"<span class='clj-var'>#&#x27;birdie-performance.core/encode-data</span>","value":"#'birdie-performance.core/encode-data"}
;; <=

;; @@
(defn plot-encode-benchmark [[name data-in-order]]
  [name (plot/bar-chart [:unoptimized
                         :one
                         :two
                         :three
                         :four
                         :five
                         :six
                         :json] data-in-order)
        (table/table-view (into [] (zipmap [:unoptimized
                                            :one
                                            :two
                                            :three
                                            :four
                                            :five
                                            :json]
                                           data-in-order ))
                          :columns ["phase" "runtime (milliseconds)"] ) 
   ])
;; @@
;; =>
;;; {"type":"html","content":"<span class='clj-var'>#&#x27;birdie-performance.core/plot-encode-benchmark</span>","value":"#'birdie-performance.core/plot-encode-benchmark"}
;; <=

;; **
;;; # Encoding Benchmarks
;;; 
;;; Things to note:
;;; 
;;; - The legend, describing the optimization phases
;;; - The rightmost bar in the bar graph is the time for native JSON encoding using `JSON.stringify`
;; **

;; @@
(table/table-view
  (concat [["legend" (table/table-view [['unoptimized "no optimizations, multimethod dispatch"]
                                        ['one "protocol dispatch and transients"]
                                        ['two "loop transients, reduce instead of apply, into vs concat"]
                                        ['three "unroll byte conversion loops, typehint boolean functions, string encode as js array"]
                                        ['four "use js arrays internally"]
                                        ['five "reuse numeric conversion arrays"
                                         'six "use reduce-kv on maps"]
                                        
                                        ]
                                       :columns ['phase 'optimizations])]]
  (map plot-encode-benchmark encode-data))
  :columns [(html/html-view "Benchmark") (html/html-view "Plot")])
;; @@
;; =>
;;; {"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"Benchmark","value":"#gorilla_repl.html.HtmlView{:content \"Benchmark\"}"},{"type":"html","content":"Plot","value":"#gorilla_repl.html.HtmlView{:content \"Plot\"}"}],"value":"[#gorilla_repl.html.HtmlView{:content \"Benchmark\"} #gorilla_repl.html.HtmlView{:content \"Plot\"}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;legend&quot;</span>","value":"\"legend\""},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-symbol'>phase</span>","value":"phase"},{"type":"html","content":"<span class='clj-symbol'>optimizations</span>","value":"optimizations"}],"value":"[phase optimizations]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>unoptimized</span>","value":"unoptimized"},{"type":"html","content":"<span class='clj-string'>&quot;no optimizations, multimethod dispatch&quot;</span>","value":"\"no optimizations, multimethod dispatch\""}],"value":"[unoptimized \"no optimizations, multimethod dispatch\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>one</span>","value":"one"},{"type":"html","content":"<span class='clj-string'>&quot;protocol dispatch and transients&quot;</span>","value":"\"protocol dispatch and transients\""}],"value":"[one \"protocol dispatch and transients\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>two</span>","value":"two"},{"type":"html","content":"<span class='clj-string'>&quot;loop transients, reduce instead of apply, into vs concat&quot;</span>","value":"\"loop transients, reduce instead of apply, into vs concat\""}],"value":"[two \"loop transients, reduce instead of apply, into vs concat\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>three</span>","value":"three"},{"type":"html","content":"<span class='clj-string'>&quot;unroll byte conversion loops, typehint boolean functions, string encode as js array&quot;</span>","value":"\"unroll byte conversion loops, typehint boolean functions, string encode as js array\""}],"value":"[three \"unroll byte conversion loops, typehint boolean functions, string encode as js array\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>four</span>","value":"four"},{"type":"html","content":"<span class='clj-string'>&quot;use js arrays internally&quot;</span>","value":"\"use js arrays internally\""}],"value":"[four \"use js arrays internally\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-symbol'>five</span>","value":"five"},{"type":"html","content":"<span class='clj-string'>&quot;reuse numeric conversion arrays&quot;</span>","value":"\"reuse numeric conversion arrays\""},{"type":"html","content":"<span class='clj-symbol'>six</span>","value":"six"},{"type":"html","content":"<span class='clj-string'>&quot;use reduce-kv on maps&quot;</span>","value":"\"use reduce-kv on maps\""}],"value":"[five \"reuse numeric conversion arrays\" six \"use reduce-kv on maps\"]"}],"value":"#gorilla_repl.table.TableView{:contents [[unoptimized \"no optimizations, multimethod dispatch\"] [one \"protocol dispatch and transients\"] [two \"loop transients, reduce instead of apply, into vs concat\"] [three \"unroll byte conversion loops, typehint boolean functions, string encode as js array\"] [four \"use js arrays internally\"] [five \"reuse numeric conversion arrays\" six \"use reduce-kv on maps\"]], :opts (:columns [phase optimizations])}"}],"value":"[\"legend\" #gorilla_repl.table.TableView{:contents [[unoptimized \"no optimizations, multimethod dispatch\"] [one \"protocol dispatch and transients\"] [two \"loop transients, reduce instead of apply, into vs concat\"] [three \"unroll byte conversion loops, typehint boolean functions, string encode as js array\"] [four \"use js arrays internally\"] [five \"reuse numeric conversion arrays\" six \"use reduce-kv on maps\"]], :opts (:columns [phase optimizations])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;small vector integers; 10,000 iterations&quot;</span>","value":"\"small vector integers; 10,000 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"07afe90f-af28-497e-9153-2607986617a5","values":[{"x":"unoptimized","y":219},{"x":"one","y":129},{"x":"two","y":64},{"x":"three","y":51},{"x":"four","y":31},{"x":"five","y":20},{"x":"six","y":20},{"x":"json","y":196}]}],"marks":[{"type":"rect","from":{"data":"07afe90f-af28-497e-9153-2607986617a5"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"07afe90f-af28-497e-9153-2607986617a5","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"07afe90f-af28-497e-9153-2607986617a5","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"07afe90f-af28-497e-9153-2607986617a5\", :values ({:x :unoptimized, :y 219} {:x :one, :y 129} {:x :two, :y 64} {:x :three, :y 51} {:x :four, :y 31} {:x :five, :y 20} {:x :six, :y 20} {:x :json, :y 196})}], :marks [{:type \"rect\", :from {:data \"07afe90f-af28-497e-9153-2607986617a5\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"07afe90f-af28-497e-9153-2607986617a5\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"07afe90f-af28-497e-9153-2607986617a5\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>219</span>","value":"219"}],"value":"[:unoptimized 219]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>129</span>","value":"129"}],"value":"[:one 129]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>64</span>","value":"64"}],"value":"[:two 64]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>51</span>","value":"51"}],"value":"[:three 51]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>31</span>","value":"31"}],"value":"[:four 31]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>20</span>","value":"20"}],"value":"[:five 20]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>20</span>","value":"20"}],"value":"[:json 20]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 219] [:one 129] [:two 64] [:three 51] [:four 31] [:five 20] [:json 20]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"small vector integers; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"07afe90f-af28-497e-9153-2607986617a5\", :values ({:x :unoptimized, :y 219} {:x :one, :y 129} {:x :two, :y 64} {:x :three, :y 51} {:x :four, :y 31} {:x :five, :y 20} {:x :six, :y 20} {:x :json, :y 196})}], :marks [{:type \"rect\", :from {:data \"07afe90f-af28-497e-9153-2607986617a5\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"07afe90f-af28-497e-9153-2607986617a5\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"07afe90f-af28-497e-9153-2607986617a5\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 219] [:one 129] [:two 64] [:three 51] [:four 31] [:five 20] [:json 20]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;small vector strings; 10,000 iterations&quot;</span>","value":"\"small vector strings; 10,000 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"a5acd31b-8654-423f-b916-95c86d0c980b","values":[{"x":"unoptimized","y":563},{"x":"one","y":374},{"x":"two","y":297},{"x":"three","y":187},{"x":"four","y":102},{"x":"five","y":56},{"x":"six","y":58},{"x":"json","y":36}]}],"marks":[{"type":"rect","from":{"data":"a5acd31b-8654-423f-b916-95c86d0c980b"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"a5acd31b-8654-423f-b916-95c86d0c980b","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"a5acd31b-8654-423f-b916-95c86d0c980b","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"a5acd31b-8654-423f-b916-95c86d0c980b\", :values ({:x :unoptimized, :y 563} {:x :one, :y 374} {:x :two, :y 297} {:x :three, :y 187} {:x :four, :y 102} {:x :five, :y 56} {:x :six, :y 58} {:x :json, :y 36})}], :marks [{:type \"rect\", :from {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>563</span>","value":"563"}],"value":"[:unoptimized 563]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>374</span>","value":"374"}],"value":"[:one 374]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>297</span>","value":"297"}],"value":"[:two 297]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>187</span>","value":"187"}],"value":"[:three 187]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>102</span>","value":"102"}],"value":"[:four 102]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>56</span>","value":"56"}],"value":"[:five 56]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>58</span>","value":"58"}],"value":"[:json 58]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 563] [:one 374] [:two 297] [:three 187] [:four 102] [:five 56] [:json 58]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"small vector strings; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"a5acd31b-8654-423f-b916-95c86d0c980b\", :values ({:x :unoptimized, :y 563} {:x :one, :y 374} {:x :two, :y 297} {:x :three, :y 187} {:x :four, :y 102} {:x :five, :y 56} {:x :six, :y 58} {:x :json, :y 36})}], :marks [{:type \"rect\", :from {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 563] [:one 374] [:two 297] [:three 187] [:four 102] [:five 56] [:json 58]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;large vector integers; 100 iterations&quot;</span>","value":"\"large vector integers; 100 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"35fe1805-8a3d-49cd-90d4-c86d05a69022","values":[{"x":"unoptimized","y":8140},{"x":"one","y":3580},{"x":"two","y":3427},{"x":"three","y":2097},{"x":"four","y":1273},{"x":"five","y":541},{"x":"six","y":536},{"x":"json","y":3042}]}],"marks":[{"type":"rect","from":{"data":"35fe1805-8a3d-49cd-90d4-c86d05a69022"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"35fe1805-8a3d-49cd-90d4-c86d05a69022","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"35fe1805-8a3d-49cd-90d4-c86d05a69022","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :values ({:x :unoptimized, :y 8140} {:x :one, :y 3580} {:x :two, :y 3427} {:x :three, :y 2097} {:x :four, :y 1273} {:x :five, :y 541} {:x :six, :y 536} {:x :json, :y 3042})}], :marks [{:type \"rect\", :from {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>8140</span>","value":"8140"}],"value":"[:unoptimized 8140]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>3580</span>","value":"3580"}],"value":"[:one 3580]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>3427</span>","value":"3427"}],"value":"[:two 3427]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>2097</span>","value":"2097"}],"value":"[:three 2097]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>1273</span>","value":"1273"}],"value":"[:four 1273]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>541</span>","value":"541"}],"value":"[:five 541]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>536</span>","value":"536"}],"value":"[:json 536]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 8140] [:one 3580] [:two 3427] [:three 2097] [:four 1273] [:five 541] [:json 536]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"large vector integers; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :values ({:x :unoptimized, :y 8140} {:x :one, :y 3580} {:x :two, :y 3427} {:x :three, :y 2097} {:x :four, :y 1273} {:x :five, :y 541} {:x :six, :y 536} {:x :json, :y 3042})}], :marks [{:type \"rect\", :from {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 8140] [:one 3580] [:two 3427] [:three 2097] [:four 1273] [:five 541] [:json 536]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;large heterogenous vector; 100 iterations&quot;</span>","value":"\"large heterogenous vector; 100 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"6bf51947-3b17-4d28-aeff-86f26192bca1","values":[{"x":"unoptimized","y":10534},{"x":"one","y":5683},{"x":"two","y":4433},{"x":"three","y":3287},{"x":"four","y":1488},{"x":"five","y":965},{"x":"six","y":949},{"x":"json","y":2590}]}],"marks":[{"type":"rect","from":{"data":"6bf51947-3b17-4d28-aeff-86f26192bca1"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"6bf51947-3b17-4d28-aeff-86f26192bca1","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"6bf51947-3b17-4d28-aeff-86f26192bca1","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :values ({:x :unoptimized, :y 10534} {:x :one, :y 5683} {:x :two, :y 4433} {:x :three, :y 3287} {:x :four, :y 1488} {:x :five, :y 965} {:x :six, :y 949} {:x :json, :y 2590})}], :marks [{:type \"rect\", :from {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>10534</span>","value":"10534"}],"value":"[:unoptimized 10534]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>5683</span>","value":"5683"}],"value":"[:one 5683]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>4433</span>","value":"4433"}],"value":"[:two 4433]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>3287</span>","value":"3287"}],"value":"[:three 3287]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>1488</span>","value":"1488"}],"value":"[:four 1488]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>965</span>","value":"965"}],"value":"[:five 965]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>949</span>","value":"949"}],"value":"[:json 949]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 10534] [:one 5683] [:two 4433] [:three 3287] [:four 1488] [:five 965] [:json 949]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"large heterogenous vector; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :values ({:x :unoptimized, :y 10534} {:x :one, :y 5683} {:x :two, :y 4433} {:x :three, :y 3287} {:x :four, :y 1488} {:x :five, :y 965} {:x :six, :y 949} {:x :json, :y 2590})}], :marks [{:type \"rect\", :from {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 10534] [:one 5683] [:two 4433] [:three 3287] [:four 1488] [:five 965] [:json 949]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;small map; 10,000 iterations&quot;</span>","value":"\"small map; 10,000 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"725807df-f228-406d-b024-37db954bc613","values":[{"x":"unoptimized","y":418},{"x":"one","y":261},{"x":"two","y":242},{"x":"three","y":176},{"x":"four","y":104},{"x":"five","y":75},{"x":"six","y":66},{"x":"json","y":145}]}],"marks":[{"type":"rect","from":{"data":"725807df-f228-406d-b024-37db954bc613"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"725807df-f228-406d-b024-37db954bc613","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"725807df-f228-406d-b024-37db954bc613","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"725807df-f228-406d-b024-37db954bc613\", :values ({:x :unoptimized, :y 418} {:x :one, :y 261} {:x :two, :y 242} {:x :three, :y 176} {:x :four, :y 104} {:x :five, :y 75} {:x :six, :y 66} {:x :json, :y 145})}], :marks [{:type \"rect\", :from {:data \"725807df-f228-406d-b024-37db954bc613\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"725807df-f228-406d-b024-37db954bc613\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"725807df-f228-406d-b024-37db954bc613\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>418</span>","value":"418"}],"value":"[:unoptimized 418]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>261</span>","value":"261"}],"value":"[:one 261]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>242</span>","value":"242"}],"value":"[:two 242]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>176</span>","value":"176"}],"value":"[:three 176]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>104</span>","value":"104"}],"value":"[:four 104]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>75</span>","value":"75"}],"value":"[:five 75]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>66</span>","value":"66"}],"value":"[:json 66]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 418] [:one 261] [:two 242] [:three 176] [:four 104] [:five 75] [:json 66]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"small map; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"725807df-f228-406d-b024-37db954bc613\", :values ({:x :unoptimized, :y 418} {:x :one, :y 261} {:x :two, :y 242} {:x :three, :y 176} {:x :four, :y 104} {:x :five, :y 75} {:x :six, :y 66} {:x :json, :y 145})}], :marks [{:type \"rect\", :from {:data \"725807df-f228-406d-b024-37db954bc613\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"725807df-f228-406d-b024-37db954bc613\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"725807df-f228-406d-b024-37db954bc613\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 418] [:one 261] [:two 242] [:three 176] [:four 104] [:five 75] [:json 66]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-string'>&quot;large map; 100 iterations&quot;</span>","value":"\"large map; 100 iterations\""},{"type":"vega","content":{"width":400,"height":247.2188,"padding":{"top":10,"left":55,"bottom":40,"right":10},"data":[{"name":"32d54912-0b7e-4735-93aa-b712e01e760c","values":[{"x":"unoptimized","y":7335},{"x":"one","y":3155},{"x":"two","y":3197},{"x":"three","y":2337},{"x":"four","y":1468},{"x":"five","y":1027},{"x":"six","y":840},{"x":"json","y":2058}]}],"marks":[{"type":"rect","from":{"data":"32d54912-0b7e-4735-93aa-b712e01e760c"},"properties":{"enter":{"x":{"scale":"x","field":"data.x"},"width":{"scale":"x","band":true,"offset":-1},"y":{"scale":"y","field":"data.y"},"y2":{"scale":"y","value":0}},"update":{"fill":{"value":"steelblue"},"opacity":{"value":1}},"hover":{"fill":{"value":"#FF29D2"}}}}],"scales":[{"name":"x","type":"ordinal","range":"width","domain":{"data":"32d54912-0b7e-4735-93aa-b712e01e760c","field":"data.x"}},{"name":"y","range":"height","nice":true,"domain":{"data":"32d54912-0b7e-4735-93aa-b712e01e760c","field":"data.y"}}],"axes":[{"type":"x","scale":"x"},{"type":"y","scale":"y"}]},"value":"#gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"32d54912-0b7e-4735-93aa-b712e01e760c\", :values ({:x :unoptimized, :y 7335} {:x :one, :y 3155} {:x :two, :y 3197} {:x :three, :y 2337} {:x :four, :y 1468} {:x :five, :y 1027} {:x :six, :y 840} {:x :json, :y 2058})}], :marks [{:type \"rect\", :from {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}}"},{"type":"list-like","open":"<center><table>","close":"</table></center>","separator":"\n","items":[{"type":"list-like","open":"<tr><th>","close":"</th></tr>","separator":"</th><th>","items":[{"type":"html","content":"<span class='clj-string'>&quot;phase&quot;</span>","value":"\"phase\""},{"type":"html","content":"<span class='clj-string'>&quot;runtime (milliseconds)&quot;</span>","value":"\"runtime (milliseconds)\""}],"value":"[\"phase\" \"runtime (milliseconds)\"]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:unoptimized</span>","value":":unoptimized"},{"type":"html","content":"<span class='clj-long'>7335</span>","value":"7335"}],"value":"[:unoptimized 7335]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:one</span>","value":":one"},{"type":"html","content":"<span class='clj-long'>3155</span>","value":"3155"}],"value":"[:one 3155]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:two</span>","value":":two"},{"type":"html","content":"<span class='clj-long'>3197</span>","value":"3197"}],"value":"[:two 3197]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:three</span>","value":":three"},{"type":"html","content":"<span class='clj-long'>2337</span>","value":"2337"}],"value":"[:three 2337]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:four</span>","value":":four"},{"type":"html","content":"<span class='clj-long'>1468</span>","value":"1468"}],"value":"[:four 1468]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:five</span>","value":":five"},{"type":"html","content":"<span class='clj-long'>1027</span>","value":"1027"}],"value":"[:five 1027]"},{"type":"list-like","open":"<tr><td>","close":"</td></tr>","separator":"</td><td>","items":[{"type":"html","content":"<span class='clj-keyword'>:json</span>","value":":json"},{"type":"html","content":"<span class='clj-long'>840</span>","value":"840"}],"value":"[:json 840]"}],"value":"#gorilla_repl.table.TableView{:contents [[:unoptimized 7335] [:one 3155] [:two 3197] [:three 2337] [:four 1468] [:five 1027] [:json 840]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}"}],"value":"[\"large map; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"32d54912-0b7e-4735-93aa-b712e01e760c\", :values ({:x :unoptimized, :y 7335} {:x :one, :y 3155} {:x :two, :y 3197} {:x :three, :y 2337} {:x :four, :y 1468} {:x :five, :y 1027} {:x :six, :y 840} {:x :json, :y 2058})}], :marks [{:type \"rect\", :from {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 7335] [:one 3155] [:two 3197] [:three 2337] [:four 1468] [:five 1027] [:json 840]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]"}],"value":"#gorilla_repl.table.TableView{:contents ([\"legend\" #gorilla_repl.table.TableView{:contents [[unoptimized \"no optimizations, multimethod dispatch\"] [one \"protocol dispatch and transients\"] [two \"loop transients, reduce instead of apply, into vs concat\"] [three \"unroll byte conversion loops, typehint boolean functions, string encode as js array\"] [four \"use js arrays internally\"] [five \"reuse numeric conversion arrays\" six \"use reduce-kv on maps\"]], :opts (:columns [phase optimizations])}] [\"small vector integers; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"07afe90f-af28-497e-9153-2607986617a5\", :values ({:x :unoptimized, :y 219} {:x :one, :y 129} {:x :two, :y 64} {:x :three, :y 51} {:x :four, :y 31} {:x :five, :y 20} {:x :six, :y 20} {:x :json, :y 196})}], :marks [{:type \"rect\", :from {:data \"07afe90f-af28-497e-9153-2607986617a5\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"07afe90f-af28-497e-9153-2607986617a5\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"07afe90f-af28-497e-9153-2607986617a5\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 219] [:one 129] [:two 64] [:three 51] [:four 31] [:five 20] [:json 20]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"small vector strings; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"a5acd31b-8654-423f-b916-95c86d0c980b\", :values ({:x :unoptimized, :y 563} {:x :one, :y 374} {:x :two, :y 297} {:x :three, :y 187} {:x :four, :y 102} {:x :five, :y 56} {:x :six, :y 58} {:x :json, :y 36})}], :marks [{:type \"rect\", :from {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"a5acd31b-8654-423f-b916-95c86d0c980b\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 563] [:one 374] [:two 297] [:three 187] [:four 102] [:five 56] [:json 58]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"large vector integers; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :values ({:x :unoptimized, :y 8140} {:x :one, :y 3580} {:x :two, :y 3427} {:x :three, :y 2097} {:x :four, :y 1273} {:x :five, :y 541} {:x :six, :y 536} {:x :json, :y 3042})}], :marks [{:type \"rect\", :from {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"35fe1805-8a3d-49cd-90d4-c86d05a69022\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 8140] [:one 3580] [:two 3427] [:three 2097] [:four 1273] [:five 541] [:json 536]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"large heterogenous vector; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :values ({:x :unoptimized, :y 10534} {:x :one, :y 5683} {:x :two, :y 4433} {:x :three, :y 3287} {:x :four, :y 1488} {:x :five, :y 965} {:x :six, :y 949} {:x :json, :y 2590})}], :marks [{:type \"rect\", :from {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"6bf51947-3b17-4d28-aeff-86f26192bca1\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 10534] [:one 5683] [:two 4433] [:three 3287] [:four 1488] [:five 965] [:json 949]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"small map; 10,000 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"725807df-f228-406d-b024-37db954bc613\", :values ({:x :unoptimized, :y 418} {:x :one, :y 261} {:x :two, :y 242} {:x :three, :y 176} {:x :four, :y 104} {:x :five, :y 75} {:x :six, :y 66} {:x :json, :y 145})}], :marks [{:type \"rect\", :from {:data \"725807df-f228-406d-b024-37db954bc613\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"725807df-f228-406d-b024-37db954bc613\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"725807df-f228-406d-b024-37db954bc613\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 418] [:one 261] [:two 242] [:three 176] [:four 104] [:five 75] [:json 66]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}] [\"large map; 100 iterations\" #gorilla_repl.vega.VegaView{:content {:width 400, :height 247.2188, :padding {:top 10, :left 55, :bottom 40, :right 10}, :data [{:name \"32d54912-0b7e-4735-93aa-b712e01e760c\", :values ({:x :unoptimized, :y 7335} {:x :one, :y 3155} {:x :two, :y 3197} {:x :three, :y 2337} {:x :four, :y 1468} {:x :five, :y 1027} {:x :six, :y 840} {:x :json, :y 2058})}], :marks [{:type \"rect\", :from {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\"}, :properties {:enter {:x {:scale \"x\", :field \"data.x\"}, :width {:scale \"x\", :band true, :offset -1}, :y {:scale \"y\", :field \"data.y\"}, :y2 {:scale \"y\", :value 0}}, :update {:fill {:value \"steelblue\"}, :opacity {:value 1}}, :hover {:fill {:value \"#FF29D2\"}}}}], :scales [{:name \"x\", :type \"ordinal\", :range \"width\", :domain {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\", :field \"data.x\"}} {:name \"y\", :range \"height\", :nice true, :domain {:data \"32d54912-0b7e-4735-93aa-b712e01e760c\", :field \"data.y\"}}], :axes [{:type \"x\", :scale \"x\"} {:type \"y\", :scale \"y\"}]}} #gorilla_repl.table.TableView{:contents [[:unoptimized 7335] [:one 3155] [:two 3197] [:three 2337] [:four 1468] [:five 1027] [:json 840]], :opts (:columns [\"phase\" \"runtime (milliseconds)\"])}]), :opts (:columns [#gorilla_repl.html.HtmlView{:content \"Benchmark\"} #gorilla_repl.html.HtmlView{:content \"Plot\"}])}"}
;; <=

;; **
;;; ## Epilogue
;;; 
;;; 
;;; So far, building and optimizing Birdie has been fun, and I've learned a lot.
;;; 
;;; It helped me to write the initial version using persistent collections that are expressive and intuitive. This made it a lot simpler to build, as I could spend most of my time on the important stuff, like understanding ETF and coming up with an information model.
;;; 
;;; From there, I was able to develop a benchmark suite and start making tweaks to various paths I guessed were hot.
;;; 
;;; Thanks to Matt Moore, Roland Cooper, Erin Greenhalgh, and David Pick for reading drafts of this post.
;;; 
;;; (INSERT CITATION OF BLOG POSTS AND OTHER THINGS THAT HELPED HERE)
;; **

;; @@

;; @@
