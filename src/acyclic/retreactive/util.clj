(ns acyclic.retreactive.util)

(defn add-ms [i ms] (java.util.Date. (+ (.getTime i) ms)))

