# retreactive

A clojure library for building distributed reactive systems that are persistent in all senses of the word.

## Usage

Don't.

## Notes

* Using girder, the 2nd element of the vector will be an instant.  Nil means now.
* Look for placeholder ```[f,nil,....]``` in ```:milestones``` using ```as-of```.
* If we find one at ```tx-time```,
   * do a regular search in ```:store``` for ```[f,tx-time,...]```.
   * if there's data, return it, along with the ```tx-time``` used.
   * while calculating it, also add our ref to ```:dep/dependents``` for each node we use
* If we don't find one:
   * Search for existence of a placeholder at current time.
   * If there is one, then this is an illegal req unless req was for nil time.
   * If time is nil, then we populate placeholder directly at current time.
* When inserting leaf nodes directly, also traverse the dependents and populate placeholders for all of them.

This is then fully reactive, lazy and replayable.  The limitation is that brand new reqs cannot in the past, because
they were never stored as dependents and thus never got placeholders.

What if an apparently legal request in the past asks for a dependency it never requested before.  Well, that will break
everything.


## License

Copyright © 2014 Peter Fraenkel

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
