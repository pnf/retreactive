(ns acyclic.retreactive.datomic
(:use acyclic.retreactive.back-end
      acyclic.retreactive.util
      [datomic.api :only [q db] :as dmc]
      [taoensso.timbre :as timbre]
      [clojure.pprint])
(:require  digest
           [clj-time.coerce :as co]
           [acyclic.girder.grid :as grid]))
(timbre/refer-timbre)

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
       nil (java.util.Date.)
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

(defn- assure-db [conn-or-db t tt]
  (let [d   (if (.isInstance datomic.peer.Connection conn-or-db)
              (db conn-or-db)
              conn-or-db)
        td  (:db/txInstant (dmc/entity d (dmc/t->tx (dmc/basis-t d))))]
    (when (or (and t  (.before td t))
              (and tt (.before td tt)))
      (throw (ex-info "Stale database" {:td td :t t :tt tt})))
    d))

(defn- get-data-at* [conn k t tt]
    (let [tf      (t-format (jd t))
          kh      (k-hash k)
          idx     (str kh idx-sep tf)
          d       (if (.isInstance datomic.peer.Connection conn) (db conn) conn) 
          d       (if (nil? tt) d (dmc/as-of d tt))
          data    (some-> (dmc/index-range d :graph/indexkey idx nil)
                          seq first)
          kf      (.v data)]
      (if (.startsWith kf kh) [data d (.e data)])))

#_(defn- get-entity-deps [d e]
  (q '[:find ?d :in $ ?e :where [?e :graph/dep ?d]] d e))

(defn- get-entity-deps [d e]
  (map :db/id (:graph/dep (dmc/entity d e))))

(defn- get-entity-leaves [d e]
  (if (seq (get-entity-deps d e)) 
    (loop [encountered #{}
           leaves      #{}
           es          #{e}]
      (if-not (seq es) leaves
              (let [ds         (map #(get-entity-deps d %) es)
                    ns         (filter (comp not encountered) (flatten ds))
                    ls         (->> (map #(vector (not (seq %1)) %2) ds es)
                                    (filter first)
                                    (map second))]
                (recur (into encountered es)
                       (into leaves ls)
                       (set (flatten ns))))))))

(defn- e->millis [d e]
  (->> e
       (dmc/entity d)
       :graph/t
       .getTime))

(defn- dirty? [d k e t tt]
  (let [ls   (get-entity-leaves d e)
        kls  (map #(:graph/key (dmc/entity d %)) ls)
        ls2  (set (map #(last (get-data-at* d % t tt)) kls))]
    (debug k e t tt kls ls ls2)
    (not= ls ls2)))

 ; ["mulberry" 303465209267180 #inst "2014-08-08T13:59:07.051-00:00" #inst "2014-08-08T13:59:07.056-00:00"]
(defn- get-at* [conn k t tt]
  (let [[data d e] (get-data-at* conn k t tt)]
    (if (or (nil? data) (dirty? d k e t tt)) nil
        (let [tt    (:db/txInstant (dmc/entity d (.tx data)))
              [v t] ((juxt :graph/value :graph/t) (dmc/entity d e))]
          [v e t tt]))))


(defrecord Datomic-Retreactive-Db [uri conn]

  Retreactive-Db

  (recreate-db [this] (->Datomic-Retreactive-Db
                       uri
                       (recreate-db* (:uri this))))

  (insert-leaf [this k value] (insert-leaf* (:conn this) k value))
  (insert-leaves [this k-values] (insert-leaves* (:conn this) k-values))
  (insert-value [this k t value deps] (println deps) (insert-value* (:conn this) k t value deps))
  (get-at [this k t] (get-at* (:conn this) k t nil))

)

(defn default-local [] (->Datomic-Retreactive-Db local-uri (dmc/connect local-uri)))
