(ns acyclic.retreactive.testscripts.misc
  (:use [datomic.api :only [q db] :as dmc]
        clojure.pprint
        [acyclic.retreactive.util]
        [acyclic.retreactive.temporal])
  (:require  digest
             [acyclic.girder.grid :as grid]
             [acyclic.retreactive.datomic :as datomic])
)


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


         (def r (datomic/default-local))

         (def  (recreate-db r))

         
         (def k1 "leaf1")
         (def tx1 (insert-leaf r k1 "mulberry"))
         (def t1 (:v (nth tx1 3)))

         (def k2 "leaf2")
         (def tx2 (insert-leaf r k2 "cabbage"))
         (def t2 (:v (nth tx2 3)))

         (def tx3 (insert-leaf r k1 "oak"))
         (def t3 (:v (nth tx3 3)))

         (assert (= "mulberry" (first (get-at r k1 t1))))
         (assert (= "mulberry" (first (get-at r k1 (add-ms t1 1)))))
         (assert (= "oak"      (first (get-at r k1 t3))))
         (assert (= "mulberry"      (first (get-at r k1 t2))))

         (def tq (add-ms t3 -1))
         (def kc1 "cricket")
         (def kc2 "grasshopper")
         (def kc3 "naturalist")
         (def qc1 (get-at r kc1 tq))
         (def qc2 (get-at r kc2 tq))
         (assert (nil? qc1))
         (assert (nil? qc2))
         (def qd1 (get-at r k1 tq)) ;; second value is entity
         (def qd2 (get-at r k2 tq)) ;; second value is entity
         (def tx3 (insert-value r kc1 tq "chester eats mulberry and oak" [(second qd1) (second qd2)]))
         (def tx4 (insert-value r kc2 tq "buster eats mulberry" [(second qd1)]))
         (def qd3 (get-at r kc1 tq))
         (def qd4 (get-at r kc2 tq))
         (def tx5 (insert-value r kc3 tq "Euell eats buster and chester"
                                 [(second qd3) (second qd4)]))
         (def qd5 (get-at r kc3 tq))
         (assert (.startsWith (first qd5) "Euell"))

         (def tx6 (insert-leaf r k1 "ivy"))
         (def t6 (:v (nth tx6 3)))

         (def qd6  (get-at r kc3 t6))
         (assert nil? qd6)
         )



