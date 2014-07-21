(ns acyclic.retreactive.datomic
(:use [datomic.api :only [q db] :as d]
      [clojure.pprint])
(:require  digest
             [clj-time.core :as ct]
             [clj-time.coerce :as co]
             ;[clj-time.format :as cf]
             ))

(def schema-tx
[{:db/id #db/id[:db.part/db]
      :db/ident :bitemp/k
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/doc "Non-temporal part of the key"
      :db.install/_attribute :db.part/db}

     {:db/id #db/id[:db.part/db]
     :db/ident :bitemp/tv
     :db/valueType :db.type/instant
     :db/cardinality :db.cardinality/one
     :db/doc "Time for which data is relevant"
     :db/index true
     :db.install/_attribute :db.part/db}

     {:db/id #db/id[:db.part/db]
      :db/ident :bitemp/index
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/doc "concatenated k and tv"
      :db/unique :db.unique/identity
      :db.install/_attribute :db.part/db}

     {:db/id #db/id[:db.part/db]
      :db/ident :bitemp/value
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :db/doc "reference to blob value"
      :db.install/_attribute :db.part/db}


     {:db/id #db/id[:db.part/db]
      :db/ident :store/type
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :db/doc "Potential or actual"
      :db.install/_attribute :db.part/db}

 [:db/add #db/id[:db.part/db] :db/ident :store.type/leaf]
 [:db/add #db/id[:db.part/db] :db/ident :store.type/potential]
 [:db/add #db/id[:db.part/db] :db/ident :store.type/complete]
 [:db/add #db/id[:db.part/db] :db/ident :store.type/reference]


     {:db/id #db/id[:db.part/db]
      :db/ident :store/value
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/doc "the blob itself"
      :db.install/_attribute :db.part/db}

     {:db/id #db/id[:db.part/db]
      :db/ident :store/dependents
      :db/valueType :db.type/ref
      :db/doc "Set of all nodes known to depend on this one."
      :db/cardinality :db.cardinality/many
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
                        :db/ident :bitemp 
                        :db.install/_partition :db.part/db}
                       {:db/id (d/tempid :db.part/db)
                        :db/ident :store
                        :db.install/_partition :db.part/db}])
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



;;   Actual tx time of leaf node is important.
;;   Whenever a leaf node is updated, insert placeholder in tv index for
;;   dependent node at tx time of leaf node.
;;   Query for dependent node will hit this placeholder and cause valuation.
;;   During recursive queries, push down full list of dependent query keys, so
;;   when we hit the leaf, can update its set.

