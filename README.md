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
[data data], (bench-fn data), 10000 runs, 231 msecs
[data data], (bench-fn data), 10000 runs, 238 msecs
[data data], (bench-fn data), 10000 runs, 284 msecs

benchmarking ETF large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 5394 msecs
[data data], (bench-fn data), 100 runs, 5395 msecs
[data data], (bench-fn data), 100 runs, 5375 msecs

benchmarking ETF large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 8779 msecs
[data data], (bench-fn data), 100 runs, 8875 msecs
[data data], (bench-fn data), 100 runs, 8720 msecs

benchmarking ETF small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 537 msecs
[data data], (bench-fn data), 10000 runs, 542 msecs
[data data], (bench-fn data), 10000 runs, 597 msecs

benchmarking ETF large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 6274 msecs
[data data], (bench-fn data), 100 runs, 6220 msecs
[data data], (bench-fn data), 100 runs, 6218 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<

>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite JSON decode benchmarks
suite contains 5 benchmarks
benchmarking JSON small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 254 msecs
[data data], (bench-fn data), 10000 runs, 192 msecs
[data data], (bench-fn data), 10000 runs, 189 msecs

benchmarking JSON large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3412 msecs
[data data], (bench-fn data), 100 runs, 3427 msecs
[data data], (bench-fn data), 100 runs, 3479 msecs

benchmarking JSON large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2846 msecs
[data data], (bench-fn data), 100 runs, 2846 msecs
[data data], (bench-fn data), 100 runs, 2906 msecs

benchmarking JSON small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 201 msecs
[data data], (bench-fn data), 10000 runs, 152 msecs
[data data], (bench-fn data), 10000 runs, 153 msecs

benchmarking JSON large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2090 msecs
[data data], (bench-fn data), 100 runs, 2115 msecs
[data data], (bench-fn data), 100 runs, 2093 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<
```


### Encoding

```
>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite ETF encode benchmarks
suite contains 5 benchmarks
benchmarking ETF small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 233 msecs
[data data], (bench-fn data), 10000 runs, 236 msecs
[data data], (bench-fn data), 10000 runs, 232 msecs

benchmarking ETF large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 8383 msecs
[data data], (bench-fn data), 100 runs, 8443 msecs
[data data], (bench-fn data), 100 runs, 8504 msecs

benchmarking ETF large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 10938 msecs
[data data], (bench-fn data), 100 runs, 10837 msecs
[data data], (bench-fn data), 100 runs, 10765 msecs

benchmarking ETF small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 450 msecs
[data data], (bench-fn data), 10000 runs, 451 msecs
[data data], (bench-fn data), 10000 runs, 439 msecs

benchmarking ETF large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 7635 msecs
[data data], (bench-fn data), 100 runs, 7702 msecs
[data data], (bench-fn data), 100 runs, 7774 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<

>>>>>>>>>>>>>>>>>>>>>>>>>>>>
running benchmark suite JSON encode benchmarks
suite contains 5 benchmarks
benchmarking JSON small homogenous vector with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 223 msecs
[data data], (bench-fn data), 10000 runs, 224 msecs
[data data], (bench-fn data), 10000 runs, 286 msecs

benchmarking JSON large homogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 3391 msecs
[data data], (bench-fn data), 100 runs, 3289 msecs
[data data], (bench-fn data), 100 runs, 3321 msecs

benchmarking JSON large heterogenous vector with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2740 msecs
[data data], (bench-fn data), 100 runs, 2758 msecs
[data data], (bench-fn data), 100 runs, 2778 msecs

benchmarking JSON small map with 10000 iterations and 5 warmup runs
[data data], (bench-fn data), 10000 runs, 153 msecs
[data data], (bench-fn data), 10000 runs, 158 msecs
[data data], (bench-fn data), 10000 runs, 205 msecs

benchmarking JSON large map with 100 iterations and 5 warmup runs
[data data], (bench-fn data), 100 runs, 2346 msecs
[data data], (bench-fn data), 100 runs, 2301 msecs
[data data], (bench-fn data), 100 runs, 2288 msecs

<<<<<<<<<<<<<<<<<<<<<<<<<<<<
```


## License

Copyright © 2018 Clark Kampfe

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
