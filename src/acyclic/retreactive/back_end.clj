(ns acyclic.retreactive.back-end)

(defprotocol Retreactive-Db

  (recreate-db [this])
  
  (insert-leaf [this k value])
  (insert-leaves [this kvs])
  (insert-value [this k t value & deps])
  (get-at [this k t & [tt]])
  (get-leaves [this k t & [tt]])
  (get-if-valid [this k t & [tt]])

)
