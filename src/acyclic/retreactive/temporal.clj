(ns acyclic.retreactive.temporal)

(defprotocol Retreactive-Db

  (recreate-db [this] "Delete database and rebuild schema.")
  (insert-leaf [this k value] "Insert a leaf as of right now")
  (insert-leaves [this kvs] "Insert a whole bunch of leaves")
  (insert-value [this k t value  deps] "Insert a value with dependencies")
  (get-at [this k t] "Get a node, nor nil if it doesn't exist or is dirty.")

)
