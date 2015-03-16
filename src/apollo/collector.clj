(ns apollo.collector
  (:require [clojure.tools.logging :as log]))

(def stats-skeleton
  {:max (Double/NEGATIVE_INFINITY)
   :min (Double/POSITIVE_INFINITY)
   :sum 0.0
   :count 0})

(def skeleton-update-fns
  {:max max
   :min min
   :sum +
   :count (fn [value _]
            (inc value))})

(def metric-coll (atom {}))

(defn- update-or-create-stats
  [curr value]
  (let [stats (or curr stats-skeleton)]
    (reduce-kv (fn [acc k v]
                 (let [updater (k skeleton-update-fns)]
                   (assoc acc k (updater v value)))) {} stats)))

(defn ingest! [{:keys [ns dimensions metric unit]} value]
  (let [path (remove nil? [ns dimensions metric unit])]
    (io! "Can not ingest during transactions."
         (get-in (swap! metric-coll
                        (fn [mc]
                          (update-in mc path update-or-create-stats value))) path))))

(defn lookup [key]
  (get-in @metric-coll key))

(defn current-collection []
  @metric-coll)

(defn vacuum!
  "Cleans the local metrics store"
  []
  (loop []
    (let [curr (current-collection)]
      (if (compare-and-set! metric-coll curr {})
        curr
        (recur)))))
