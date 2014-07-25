(ns acyclic.retreactive.datomic
(:use [datomic.api :only [q db] :as d]
      [clojure.pprint])
(:require  digest
             [clj-time.core :as ct]
             [clj-time.coerce :as co]
             ;[clj-time.format :as cf]
             ))


;; The trouble with this is that it assumes same dependents across time.
(def schema-tx
  [
   ;; milestones
   ;; accessed with-as of, and populated during insert of leaf data
   ;; record is inserted for leaf data itself and for dependents
   {:db/id #db/id[:db.part/db]
    :db/ident :milestones/key
    :db/valueType :db.type/string
    :db/doc "Key, stripped of time"
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :milestones/uuid
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :milestones/dummy
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}


;; @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key "howdy" :milestones/dummy (d/squuid)}])
;; (q '[:find ?k ?t ?du :where [?e :milestones/key "howdy"] [?e :milestones/key ?k] [?e :milestones/dummy ?du ?tx] [?tx :db/txInstant ?t]] (db conn))
;;(q '[:find ?e ?k ?t ?du :where [?e :milestones/key "howdy"] [?e :milestones/key ?k] [?e :milestones/dummy ?du ?tx] [?tx :db/txInstant ?t]] (d/as-of (db conn) #inst "2014-07-24T17:22:10.990-00:00"))


   {:db/id #db/id[:db.part/db]
    :db/ident :dep/node
    :db/valueType :db.type/ref
    :db/doc "Reference to store entity"
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :dep/dependents
    :db/valueType :db.type/ref
    :db/doc "Reference to store entities - populated during calculations"
    :db/cardinality :db.cardinality/many
    :db/isComponent true
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :store/key
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/id #db/id[:db.part/db]
    :db/ident :store/value
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

  {:db/id #db/id[:db.part/db]
     :db/ident :store/t
     :db/valueType :db.type/instant
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db}
]

)


(def uri "datomic:free://localhost:4334/acyclic")

(defn recreate-db [uri]
  (d/delete-database uri)
  (d/create-database uri)
  (let [conn (d/connect uri)]
    @(d/sync conn)
    @(d/transact conn schema-tx)
    @(d/transact conn [{:db/id (d/tempid :db.part/db)
                        :db/ident :milestones
                        :db.install/_partition :db.part/db}
                       {:db/id (d/tempid :db.part/db)
                        :db/ident :store
                        :db.install/_partition :db.part/db}
                       {:db/id (d/tempid :db.part/db)
                        :db/ident :dep
                        :db.install/_partition :db.part/db}

])
    conn))


(defn connect [uri]
  (let [conn (d/connect uri)]
    @(d/sync conn)
    conn))

(defn k-hash [k] (digest/md5 k))
(defn long->str [l] 
  (let [li (- 100000000000000 l)]
    (apply str (map #(->> % (bit-shift-right li) (bit-and 0xFF) char) [56 48 40 32 24 16 8 0]))))
(defn t-format [t] (-> t .getTime long->str))
(def idx-sep "-")
(defn idxid [k tv] (str (k-hash k) idx-sep (t-format tv)))

(defn  jd 
  ([tv]
     (condp = (type tv)
       java.lang.Long (java.util.Date. tv)
       java.lang.Integer (java.util.Date. (long tv))
       java.util.Date tv
       java.lang.String (-> tv co/from-string co/to-date)))
  ([] (java.util.Date.)))


(defn insert-bitemp-tx [k tv value] 
  (let [tv (jd tv)
      idx (idxid k tv)]
    {:db/id (d/tempid :bitemp) ;#db/id[:user.part/users]
                          :bitemp/index idx
                          :bitemp/k k
                          :bitemp/tv tv
                          :bitemp/value value}))



(defn add-ms [i ms] (java.util.Date. (+ (.getTime i) ms)))

(comment

  (def conn (recreate-db uri))


  ;; Demonstrate uuid is necessary to avoid always getting the same tx time
  (def tx-nouiid1  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key "leaf1"}])))
  (def t1 (-> tx-nouiid1 first :v))
  (Thread/sleep 1000)
  (def tx-nouiid2  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key "leaf1"}])))
  (def t2 (-> tx-nouiid2 first :v))
  (Thread/sleep 1000)
  (def tx-nouiid3  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key "leaf11"}])))
  (def t3 (-> tx-nouiid3 first :v))

  (def t2? (-> (q '[:find ?t :where [?e :milestones/key "leaf1" ?tx] [?tx :db/txInstant ?t]] (d/as-of (db conn) (add-ms t3 -1)))
               first first))
  (assert (= t1 t2?))

  ;; same thing, but with uuid
  (def tx-nouiid1  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key  "leaf1" :milestones/uuid (d/squuid)}])))
  (def t1 (-> tx-nouiid1 first :v))
  (Thread/sleep 1000)
  (def tx-nouiid2  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key  "leaf1" :milestones/uuid (d/squuid)}])))
  (def t2 (-> tx-nouiid2 first :v))
  (Thread/sleep 1000)
  (def tx-nouiid3  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key  "leaf1" :milestones/uuid (d/squuid)}])))
  (def t3 (-> tx-nouiid3 first :v))
  (def t2? (-> (q '[:find ?t :where [?e :milestones/key "leaf1"] [?e :milestones/uuid _ ?tx] [?tx :db/txInstant ?t]] (d/as-of (db conn) (add-ms t3 -1)))
               first first))
  (assert (= t2 t2?))



    
    ;; create some data

    (def tx1a  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key "leaf1" :milestones/uuid (d/squuid)}])))
    (def t1 (-> tx1a first :v))
    (def tx1b (:tx-data @(d/transact conn [{:db/id (d/tempid :store) :store/key "leaf1" :store/t t1 :store/value "hello"}])))

    (Thread/sleep 5000)

    ;; update it

    (def tx2a  (:tx-data @(d/transact conn [{:db/id (d/tempid :milestones)  :milestones/key "leaf1" :milestones/uuid (d/squuid)}])))
    (def t2 (-> tx2a first :v))
    (def tx2b (:tx-data @(d/transact conn [{:db/id (d/tempid :store) :store/key "leaf1" :store/t t1 :store/value "goodbye"}])))

    ;; Find data as of a moment before second milestone
    (q '[:find ?t :where [?e :milestones/key "leaf1"] [?e :milestones/uuid _ ?tx] [?tx :db/txInstant ?t]] (d/as-of (db conn) (add-ms t2 -1) ))

    




    ;;   Actual tx time of leaf node is important.
    ;;   Whenever a leaf node is updated, insert placeholder in tv index for
    ;;   dependent node at tx time of leaf node.
    ;;   Query for dependent node will hit this placeholder and cause valuation.
    ;;   During recursive queries, push down full list of dependent query keys, so
    ;;   when we hit the leaf, can update its set.

    )





