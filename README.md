# birdie

A Clojurescript library for converting to and from [Erlang's External Term Format](http://erlang.org/doc/apps/erts/erl_ext_dist.html).
Maybe [BERT](http://bert-rpc.org/) if I get around to it.

## Usage

```clj
cljs.user> (require '[birdie.core :as b])
nil
cljs.user> (b/encode {:hi "there"})
(131 116 0 0 0 1 119 2 104 105 109 0 0 0 5 116 104 101 114 101)
cljs.user> (b/decode (vector 131 116 0 0 0 1 119 2 104 105 109 0 0 0 5 116 104 101 114 101))
{:hi "there"}
cljs.user> (b/encode {:hi "there"} {:kind :array})
#js [131 116 0 0 0 1 119 2 104 105 109 0 0 0 5 116 104 101 114 101]
cljs.user> (b/encode {:hi "there"} {:kind :typed-array})
#object[Uint8Array 131,116,0,0,0,1,119,2,104,105,109,0,0,0,5,116,104,101,114,101]
```


## Type correspondence

Note that this library does not currently support the entire set of encodable/decodable Erlang terms.
It supports a subset that is roughly equivalent to those present in JSON.
Also note that the API works on native Clojurescript datastructures/types, like `[1 2 3]`,
not on proxy/wrapper types like a hypothetical `(->birdie.List [1 2 3])` or something.
There really is no reason for this other than this is how I've implemented this version.
If there is a compelling reason to do otherwise, I might consider changing it. I may have to include
one or two proxy types for the odd-but-common case of Clojurescript not having an equivalent of
Erlang's `tuple` type. See below, as well as the tests for more information.

```
| Clojurescript                | Erlang/ETF                        |
| js/string                    | binary                            |
| js/number                    | new float, small integer, integer |
| js/boolean                   | atom (regular)                    |
| cljs.core/Keyword            | atom (utf8)                       |
| cljs.core/PersistentVector   | list                              |
| cljs.core/List               | list                              |
| cljs.core/EmptyList          | list                              |
| cljs.core/PersistentHashSet  | list                              |
| cljs.core/PersistentHashMap  | map                               |
| cljs.core/PersistentArrayMap | map                               |
```

The encode stack implements `birdie.encode.Encodable` for the above types, so you don't have
to wait for me if you want to encode a new/different type (or override one of the above) and send it to Erlang.
Just `(extend-protocol birdie.encode.Encodable YourType (do-encode [this] ...))` and you're off to the races.

The decode stack dispatches raw bytes against a `case` statement for performance, so unfortunately
that one is not user extendable. That said, it should decode most things from Erlang that are
usable Clojurescript/Javascript values. I'll be working to determine Clojurescript/Javascript
representations of things like `port`, `pid` and `reference` as I get the time, but they are
not high priorities.

## Benchmarks

To run the benchmarks:

```
$ brew update && brew install lumo
$ ./bench.sh
```

### Decoding

```
>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite ETF decode benchmarks
suite contains 6 benchmarks
benchmarking ETF small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 47 msecs
[data data], (bench-fn data), 10000 runs, 50 msecs
[data data], (bench-fn data), 10000 runs, 53 msecs

benchmarking ETF small homogenous string vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 158 msecs
[data data], (bench-fn data), 10000 runs, 160 msecs
[data data], (bench-fn data), 10000 runs, 169 msecs

benchmarking ETF large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 1867 msecs
[data data], (bench-fn data), 100 runs, 1821 msecs
[data data], (bench-fn data), 100 runs, 1864 msecs

benchmarking ETF large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2375 msecs
[data data], (bench-fn data), 100 runs, 2386 msecs
[data data], (bench-fn data), 100 runs, 2426 msecs

benchmarking ETF small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 147 msecs
[data data], (bench-fn data), 10000 runs, 133 msecs
[data data], (bench-fn data), 10000 runs, 139 msecs

benchmarking ETF large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 1814 msecs
[data data], (bench-fn data), 100 runs, 1795 msecs
[data data], (bench-fn data), 100 runs, 1747 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<

>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite JSON decode benchmarks
suite contains 6 benchmarks
benchmarking JSON small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 215 msecs
[data data], (bench-fn data), 10000 runs, 213 msecs
[data data], (bench-fn data), 10000 runs, 205 msecs

benchmarking JSON small homogenous string vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 62 msecs
[data data], (bench-fn data), 10000 runs, 60 msecs
[data data], (bench-fn data), 10000 runs, 60 msecs

benchmarking JSON large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3721 msecs
[data data], (bench-fn data), 100 runs, 3687 msecs
[data data], (bench-fn data), 100 runs, 3771 msecs

benchmarking JSON large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2907 msecs
[data data], (bench-fn data), 100 runs, 2978 msecs
[data data], (bench-fn data), 100 runs, 2909 msecs

benchmarking JSON small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 176 msecs
[data data], (bench-fn data), 10000 runs, 165 msecs
[data data], (bench-fn data), 10000 runs, 221 msecs

benchmarking JSON large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2220 msecs
[data data], (bench-fn data), 100 runs, 2243 msecs
[data data], (bench-fn data), 100 runs, 2234 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<
```


### Encoding

```
>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite ETF encode benchmarks
suite contains 6 benchmarks
benchmarking ETF small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 136 msecs
[data data], (bench-fn data), 10000 runs, 139 msecs
[data data], (bench-fn data), 10000 runs, 134 msecs

benchmarking ETF small homogenous string vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 409 msecs
[data data], (bench-fn data), 10000 runs, 403 msecs
[data data], (bench-fn data), 10000 runs, 405 msecs

benchmarking ETF large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3693 msecs
[data data], (bench-fn data), 100 runs, 3709 msecs
[data data], (bench-fn data), 100 runs, 3822 msecs

benchmarking ETF large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 6110 msecs
[data data], (bench-fn data), 100 runs, 6044 msecs
[data data], (bench-fn data), 100 runs, 6100 msecs

benchmarking ETF small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 292 msecs
[data data], (bench-fn data), 10000 runs, 303 msecs
[data data], (bench-fn data), 10000 runs, 285 msecs

benchmarking ETF large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3415 msecs
[data data], (bench-fn data), 100 runs, 3456 msecs
[data data], (bench-fn data), 100 runs, 3498 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<

>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite JSON encode benchmarks
suite contains 6 benchmarks
benchmarking JSON small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 217 msecs
[data data], (bench-fn data), 10000 runs, 235 msecs
[data data], (bench-fn data), 10000 runs, 256 msecs

benchmarking JSON small homogenous string vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 39 msecs
[data data], (bench-fn data), 10000 runs, 39 msecs
[data data], (bench-fn data), 10000 runs, 38 msecs

benchmarking JSON large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3300 msecs
[data data], (bench-fn data), 100 runs, 3198 msecs
[data data], (bench-fn data), 100 runs, 3321 msecs

benchmarking JSON large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2733 msecs
[data data], (bench-fn data), 100 runs, 2787 msecs
[data data], (bench-fn data), 100 runs, 2776 msecs

benchmarking JSON small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 151 msecs
[data data], (bench-fn data), 10000 runs, 160 msecs
[data data], (bench-fn data), 10000 runs, 178 msecs

benchmarking JSON large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2370 msecs
[data data], (bench-fn data), 100 runs, 2282 msecs
[data data], (bench-fn data), 100 runs, 2348 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<
```

## Todo

- [ ] figure out what to do about tuples
- [ ] should the API be based on records rather than Clojure builtin types? (`(->birdie.List 1 2 3)` vs `[1 2 3]`)
- [ ] can encode be optimized?
- [ ] is encode being `cljs -> bytevector` the right API? Should it be `cljs-> string` or `cljs -> js array` or `cljs -> js typed array`?
- [ ] blog post explaining optimization process
- [ ] publish artifact to...cljsjs? npm?


## License

Copyright © 2018 Clark Kampfe

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
