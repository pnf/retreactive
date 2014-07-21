# retreactive

A Clojure library designed to ... well, that part is up to you.

## Usage

Don't.

## Notes

* Every req is evaluated using the same clojure function.
* The req includes the time for which a result is desired.
* The trail of requests is passed down along recursive queries, so the reqfn receives a list.  (How? Part of state value for :running?)
* The reqfn performs a temporal lookup, finding a uuid value and state: leaf, potential, complete (or maybe nothing)
* If the result is a leaf, then add to the leaf's set of dependent keys and return the value.
* If the result is complete, just return its value.
* If the result is potential, then continue evaluation.
* When a leaf is added, do temporal search for previous.
* For each dependent keys of the leaf, add a "potential" at the leaf's time to the temporal database.
* For brand new calculations, we obviously find nothing.



## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
