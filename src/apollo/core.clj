(ns apollo.core
  (:require [clojure.tools.logging :as log]
            [clojure.string :as st]
            [apollo.collector :as collector]
            [apollo.cloudwatch :as cw])
  (:import [com.amazonaws.metrics AwsSdkMetrics]
           [java.util.concurrent Executors TimeUnit ScheduledExecutorService]))

(defn create-cw-client
  [& opts]
  (apply cw/create-client opts))

(defn create-async-cw-client
  [& opts]
  (apply cw/create-async-client opts))

(def max-req-datums 20)

(defn- to-cloudwatch
  [namespace datum-batch client]
  (let [req (apply cw/gen-put-data-req namespace datum-batch)]
    (cw/put-metric-data client req)))

(defn name-stats->datums
  [dimensions name-stats]
  (mapcat (fn [[metric-name unit-stats]]
            (reduce-kv (fn [acc unit stats]
                         (let [statistic-set (cw/gen-stat-set stats)]
                           (conj acc (cw/gen-datum metric-name
                                                   :stats statistic-set
                                                   :dimensions dimensions
                                                   :unit (name unit))))) [] unit-stats)) name-stats))

(defn dim-stats->datums
  [dim-stats]
  (mapcat (fn [[dim-map name-stats]]
            (let [dimensions (reduce-kv (fn [acc k v]
                                          (conj acc (cw/gen-dimension k v)))
                                        [] dim-map)]
              (name-stats->datums dimensions name-stats))) dim-stats))

(defn- collection->datum-map
  [collection]
  (reduce-kv (fn [acc namespace dim-stats]
               (assoc acc namespace (partition-all max-req-datums
                                                   (dim-stats->datums dim-stats))))
             {} collection))

(defn- build-and-record!
  [client collection]
  (try
    (let [datum-map (collection->datum-map collection)]
      (reduce-kv (fn [acc namespace datum-coll]
                   (let [cleaned-ns (st/replace namespace #"^\." "")
                         results (map #(to-cloudwatch cleaned-ns % client) datum-coll)]
                     (into [] (concat acc results))))
                 [] datum-map))
    (catch com.amazonaws.AmazonClientException ae
      (if (.isRetryable ae)
        (do (log/warn (format "Amazon Cloudwatch Client exception encountered; is retryable. Sleeping 5 seconds before retry. %s"ae))
            (Thread/sleep 5000)
            (build-and-record! client collection))
        (log/error (.getMessage ae))))
    (catch Exception e
      (log/error "Error recording metrics to Cloudwatch!")
      (log/error (.getMessage e)))
    (catch clojure.lang.ExceptionInfo ex
      (log/error "Error recording metrics to Cloudwatch!")
      (log/error (ex-data ex)))))

(defn vacuum!
  [client]
  (log/debug "Vacuuming metrics...")
  (let [collection (collector/vacuum!)]
    (build-and-record! client collection)))

(defn record!
  [namespace dimensions metric-name value unit]
  (log/debug (format "Recording metric %s for %s: %s %s %s" metric-name namespace dimensions value unit))
  (collector/ingest! {:ns namespace
                      :dimensions dimensions
                      :metric (name metric-name)
                      :unit (keyword unit)} value))

(defn- build-ns-str
  [current namespace]
  (if namespace
    (let [ns-str (if (keyword? namespace)
                   (name namespace)
                   namespace)]
      (if (re-find #"^\..*" ns-str)
        ns-str
        (apply str current "." ns-str)))
    current))

(defn- build-dimension-map
  [current dimensions]
  (let [curr-keys (map keyword (keys current))
        curr-vals (map name (vals current))
        dim-keys (map keyword (keys dimensions))
        dim-vals (map name (vals dimensions))]
    (merge (zipmap curr-keys curr-vals) (zipmap dim-keys dim-vals))))


(defn get-context-recorder
  [ns-context dims-context]
  (fn [metric-name value unit & {:keys [namespace dimensions]}]
    (let [namespace* (build-ns-str ns-context namespace)
          current-dim-context (if namespace
                                {}
                                dims-context)
          dimensions* (build-dimension-map current-dim-context (or dimensions {}))]
      (record! namespace* dimensions* metric-name value unit))))

(defn get-context-inc-recorder
  [ns-context dims-context]
  (fn [metric-name & {:keys [namespace dimensions]}]
    (let [namespace* (build-ns-str ns-context namespace)
          current-dim-context (if namespace
                                {}
                                dims-context)
          dimensions* (build-dimension-map current-dim-context (or dimensions {}))]
      (record! namespace* dimensions* metric-name 1.0 :Count))))

(defn get-context-dec-recorder
  [ns-context dims-context]
  (fn [metric-name & {:keys [namespace dimensions]}]
    (let [namespace* (build-ns-str ns-context namespace)
          current-dim-context (if namespace
                                {}
                                dims-context)
          dimensions* (build-dimension-map current-dim-context (or dimensions {}))]
      (record! namespace* dimensions* metric-name -1.0 :Count))))

(defn stop-vacuum-scheduler! [^ScheduledExecutorService scheduler]
  (log/info "Metrics vacuum scheduler shutting down.")
  (.shutdownNow scheduler))

(defn start-vacuum-scheduler! [delay-secs interval-secs ^ScheduledExecutorService scheduler client]
  (log/info "Starting metrics vacuum scheduler.")
  (.scheduleWithFixedDelay
   scheduler
   #(vacuum! client)
   delay-secs interval-secs TimeUnit/SECONDS))

(defn create-vacuum-scheduler []
  (Executors/newSingleThreadScheduledExecutor))

(defn enable-sys-metrics!
  [ns-prefix]
  (AwsSdkMetrics/setMetricNameSpace (st/join "." (conj ns-prefix "AWSSDK")))
  (AwsSdkMetrics/enableDefaultMetrics))
