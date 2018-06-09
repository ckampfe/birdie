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
| birdie.encode/Tuple          | tuple                             |
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

## Todo

- [x] figure out what to do about tuples
- [x] should the API be based on records rather than Clojure builtin types? (`(->birdie.List 1 2 3)` vs `[1 2 3]`)
- [x] can encode be optimized?
- [x] is encode being `cljs -> bytevector` the right API? Should it be `cljs-> string` or `cljs -> js array` or `cljs -> js typed array`?
- [ ] blog post explaining optimization process
- [ ] publish artifact to...cljsjs? npm?
- [ ] implement compression


## License

Copyright © 2018 Clark Kampfe

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
