(ns acyclic.retreactive.datomic
(:use acyclic.retreactive.back-end
      [datomic.api :only [q db] :as dmc]
      [clojure.pprint])
(:require  digest
           [acyclic.girder.grid :as grid]))

(def schema-tx [{:db/id #db/id[:db.part/db]
                       :db/ident :graph/key
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db.install/_attribute :db.part/db}

                      {:db/id #db/id[:db.part/db]
                       :db/ident :graph/t
                       :db/valueType :db.type/instant
                       :db/cardinality :db.cardinality/one
                       :db.install/_attribute :db.part/db}
                      
                      {:db/id #db/id[:db.part/db]
                       :db/ident :graph/indexkey
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db/doc "Distorted key for temporal lookups."
                       :db/unique :db.unique/value
                       :db.install/_attribute :db.part/db}

                      {:db/id #db/id[:db.part/db]
                       :db/ident :graph/dep
                       :db/valueType :db.type/ref
                       :db/cardinality :db.cardinality/many
                       :db.install/_attribute :db.part/db}

                      {:db/id #db/id[:db.part/db]
                       :db/ident :graph/value
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db.install/_attribute :db.part/db}

                      {:db/id #db/id[:db.part/db]
                       :db/ident :graph/clock
                       :db/valueType :db.type/uuid
                       :db/cardinality :db.cardinality/one
                       :db/doc "Only purpose is to keep track of time"
                       :db.install/_attribute :db.part/db}

                      {:db/id #db/id[:db.part/db]
                       :db/ident :graph
                       :db.install/_partition :db.part/db}
                      ])




(def local-uri "datomic:free://localhost:4334/acyclic")

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


(defn- insert-tx
  ([k t value] (insert-tx k t value nil))
  ([k t value deps] 
     (let [t (jd t)
           idx (idxid k t)
           tid (dmc/tempid :graph)
           tx  [{:db/id tid
                  :graph/indexkey idx
                  :graph/key k
                  :graph/t t
                  :graph/value value}]
           td   (map (fn [d] {:db/id tid :graph/dep d}) deps)]
       (vec (concat tx td)))))

(defn- graph-time [conn]
(-> @(dmc/transact conn [{:db/id (dmc/tempid :graph) :graph/clock (dmc/squuid)}])
    :tx-data first :v))



(defn- recreate-db* [uri]
    (dmc/delete-database uri)
    (dmc/create-database uri)
    (let [conn (dmc/connect uri)]
      @(dmc/transact conn schema-tx)
      conn))

(defn- insert-leaf* [conn k value]
    (:tx-data @(dmc/transact conn (insert-tx k (graph-time conn) value))))

(defn- insert-leaves* [conn k-values]
    (let [t    (graph-time conn)]
      (:tx-data @(dmc/transact conn
                               (map (partial insert-tx t) k-values)))))

(defn- insert-value* [conn k t value deps]
    (:tx-data @(dmc/transact conn (insert-tx k t value deps))))

(defn- get-at* [conn k t tt]
    (let [tf  (t-format (jd t))
          kh  (k-hash k)
          idx (str kh idx-sep tf)
          d   (if (.isInstance datomic.peer.Connection conn) (db conn) conn) 
          d   (if (nil? tt) d (dmc/as-of d tt))
          es  (some-> (dmc/index-range d :graph/indexkey idx nil)
                      seq first .v)]
      (or  (and es
                (.startsWith es kh)
                (first (q '[:find ?v ?e ?t ?tt
                            :in $ ?i ?k
                            :where
                            [?e :graph/indexkey ?i ?tx]
                            [?tx :db/txInstant ?tt]
                            [?e :graph/key   ?k]
                            [?e :graph/value ?v]
                            [?e :graph/t     ?t]]
                          d es k)))
           nil)))

(defn- get-entity-at* [conn k t tt]
    (let [tf  (t-format (jd t))
          kh  (k-hash k)
          idx (str kh idx-sep tf)
          d   (if (.isInstance datomic.peer.Connection conn) (db conn) conn) 
          d   (if (nil? tt) d (dmc/as-of d tt))
          e   (some-> (dmc/index-range d :graph/indexkey idx nil)
                      seq first .e)]
      e))

(defn- get-leaves* [e]
  (q '[:find ?d :with ?e :where [?e :graph/dep ?d]] d ec)  YOU STPPPED HERE
  (loop [e      e
         leaves ()]
    ()

)

)


  
;; #_


;; #_(defn graph-time [conn]
;;   (-> @(dmc/transact conn [{:db/id (dmc/tempid :graph) :graph/clock (dmc/squuid)}]) :tx-data first :v)
;; )

;; #_(defn graph-time [conn]
;;   (let [xn  (-> @(dmc/transact conn [{:db/id (dmc/tempid :graph) :graph/clock (dmc/squuid)}]) :tx-data first :v)@(dmc/transact conn [{:db/id (dmc/tempid :graph) :graph/clock (dmc/squuid)}])
;;         txd (:tx-data xn)
;    (-> txd first :v)


(defrecord Datomic-Retreactive-Db [uri conn]

  Retreactive-Db

  (recreate-db [this] (recreate-db* (:uri this)))

  (connect [this]
    (let [uri (:uri this)
          conn (dmc/connect uri)]
      conn))

  (insert-leaf [this k value] (insert-leaf* (:conn this)))

  (insert-leaves [this k-values] (insert-leaves* (:conn this) k-values))

  (insert-value [this k t value & deps] (insert-value* (:conn this) k t value deps))


  (get-at [this k t & [tt]] (get-at* (:conn this) k t tt))

  #_(get-if-valid [this k t & [tt]]
    (let [[v e t t tt] (get-at k t tt)
])))

(defn default-local [] (->Datomic-Retreactive-Db local-uri (dmc/connect local-uri)))
