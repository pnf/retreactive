(ns acyclic.retreactive.datomic

(:use [datomic.api :only [q db] :as dmc]
      clojure.pprint
      [acyclic.retractive.back-end])

(:require  digest
           [acyclic.girder.grid :as grid]

           [acyclic.retreactive.datomic :datomic]))


(comment "Storing dependencies, but algorithm only pays attention to leaf time."

         ;; Fundamental problem:
         ;; We need to be able to insert results at the query time, or else we lose
         ;; referential transparency (i.e. should data added during the calculation
         ;; be part of the result) and laziness (by definition, we cannot insert in the past).
         ;; So we have to use our own times database. BUT, we also need to make sure that no
         ;; leaf data can be added in the past, since that will destroy validity of past
         ;; calculations.

         ;; Leaf insertion goes in two phases:
         ;; 1) Insert the dummy to sync'd db.
         ;; 2) Insert the value with t=placeholder's tx time.
         ;; Query goes in two phases:
         ;; 1) Insert the dummy to sync'd db.
         ;; 2) If query is before the dummy's tx-time time, then use as-is in the temporal store.
         ;; 3) If query is nil, use the sync'd time.
         ;; 4) If query is after sync'd time, reject it as error.
         ;; This is the only way we can allow lazy evaluation with query times in the past.
         ;; Note that this is unitemporal.  Nothing interesting happens by scrolling back in time.


         (def r (datomic/local-default))

         (recreate-db r)


         (def conn (recreate-db* local-uri))
         
         (def k1 "leaf1")
         (def tx1 (insert-leaf* conn k1 "mulberry"))
         (def t1 (:v (nth tx1 3)))

         (def k2 "leaf2")
         (def tx2 (insert-leaf* conn k2 "cabbage"))
         (def t2 (:v (nth tx2 3)))

         (def tx3 (insert-leaf* conn k1 "oak"))
         (def t3 (:v (nth tx3 3)))

         (def d (db conn))
         (assert (= "mulberry" (first (get-at* d k1 t1 nil))))
         (assert (= "mulberry" (first (get-at* d k1 (add-ms t1 1) nil))))
         (assert (= "oak"      (first (get-at* d k1 t3 nil))))
         (assert (= "mulberry"      (first (get-at* d k1 t2 nil ))))

         (def tq (add-ms t3 -1))
         (def kc1 "cricket")
         (def kc2 "grasshopper")
         (def kc3 "naturalist")
         (def qc1 (get-at* conn kc1 tq nil))
         (def qc2 (get-at* conn kc2 tq nil))
         (assert (nil? qc1))
         (assert (nil? qc2))
         (def qd1 (get-at* conn k1 tq nil))  ;; second value is entity
         (def qd2 (get-at* conn k2 tq nil))  ;; second value is entity
         (def tx3 (insert-value* conn kc1 tq "chester eats mulberry and oak" [(second qd1) (second qd2)]))
         (def tx4 (insert-value* conn kc2 tq "buster eats mulberry" [(second qd1)]))
         (def qd3 (get-at* conn kc1 tq nil))
         (def qd4 (get-at* conn kc2 tq nil))
         (def tx5 (insert-value* conn kc3 tq "Euell eats buster and chester"
                                 [(second qd3) (second qd4)]))
         (def qd5 (get-at* conn kc3 tq nil))
         (assert (.startsWith (first qd5) "Euell"))

         (def tx6 (insert-leaf* conn k1 "ivy"))
         (def t6 (:v (nth tx6 3)))

         (def qd6  (get-at* conn kc3 t6 nil))
         (assert nil? qd6)


)




