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

Note that this library does not currently support the entire set of encodable/decodable Erlang terms. It supports a subset that is roughly equivalent to those present in JSON. See the tests for more information.

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
suite contains 5 benchmarks
benchmarking ETF small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 99 msecs
[data data], (bench-fn data), 10000 runs, 95 msecs
[data data], (bench-fn data), 10000 runs, 99 msecs

benchmarking ETF large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3056 msecs
[data data], (bench-fn data), 100 runs, 3034 msecs
[data data], (bench-fn data), 100 runs, 3083 msecs

benchmarking ETF large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 4039 msecs
[data data], (bench-fn data), 100 runs, 4121 msecs
[data data], (bench-fn data), 100 runs, 4025 msecs

benchmarking ETF small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 275 msecs
[data data], (bench-fn data), 10000 runs, 289 msecs
[data data], (bench-fn data), 10000 runs, 276 msecs

benchmarking ETF large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3422 msecs
[data data], (bench-fn data), 100 runs, 3291 msecs
[data data], (bench-fn data), 100 runs, 3344 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<

>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite JSON decode benchmarks
suite contains 5 benchmarks
benchmarking JSON small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 199 msecs
[data data], (bench-fn data), 10000 runs, 205 msecs
[data data], (bench-fn data), 10000 runs, 209 msecs

benchmarking JSON large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3630 msecs
[data data], (bench-fn data), 100 runs, 3646 msecs
[data data], (bench-fn data), 100 runs, 3634 msecs

benchmarking JSON large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2841 msecs
[data data], (bench-fn data), 100 runs, 2949 msecs
[data data], (bench-fn data), 100 runs, 2895 msecs

benchmarking JSON small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 174 msecs
[data data], (bench-fn data), 10000 runs, 190 msecs
[data data], (bench-fn data), 10000 runs, 192 msecs

benchmarking JSON large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2226 msecs
[data data], (bench-fn data), 100 runs, 2218 msecs
[data data], (bench-fn data), 100 runs, 2284 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<

```


### Encoding

```
>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite ETF encode benchmarks
suite contains 5 benchmarks
benchmarking ETF small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 140 msecs
[data data], (bench-fn data), 10000 runs, 138 msecs
[data data], (bench-fn data), 10000 runs, 131 msecs

benchmarking ETF large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3602 msecs
[data data], (bench-fn data), 100 runs, 3589 msecs
[data data], (bench-fn data), 100 runs, 3597 msecs

benchmarking ETF large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 5991 msecs
[data data], (bench-fn data), 100 runs, 5907 msecs
[data data], (bench-fn data), 100 runs, 5980 msecs

benchmarking ETF small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 279 msecs
[data data], (bench-fn data), 10000 runs, 280 msecs
[data data], (bench-fn data), 10000 runs, 283 msecs

benchmarking ETF large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3433 msecs
[data data], (bench-fn data), 100 runs, 3337 msecs
[data data], (bench-fn data), 100 runs, 3475 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<

>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite JSON encode benchmarks
suite contains 5 benchmarks
benchmarking JSON small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 217 msecs
[data data], (bench-fn data), 10000 runs, 272 msecs
[data data], (bench-fn data), 10000 runs, 203 msecs

benchmarking JSON large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3213 msecs
[data data], (bench-fn data), 100 runs, 3220 msecs
[data data], (bench-fn data), 100 runs, 3124 msecs

benchmarking JSON large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2704 msecs
[data data], (bench-fn data), 100 runs, 2717 msecs
[data data], (bench-fn data), 100 runs, 2673 msecs

benchmarking JSON small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 149 msecs
[data data], (bench-fn data), 10000 runs, 151 msecs
[data data], (bench-fn data), 10000 runs, 153 msecs

benchmarking JSON large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2237 msecs
[data data], (bench-fn data), 100 runs, 2277 msecs
[data data], (bench-fn data), 100 runs, 2228 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<
```


## License

Copyright © 2018 Clark Kampfe

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
