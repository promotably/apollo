(ns apollo.cloudwatch
  (:require [clojure.tools.logging :as log])
  (:import [java.util Date]
           [com.amazonaws.services.cloudwatch
            AmazonCloudWatchClient AmazonCloudWatchAsyncClient]
           [com.amazonaws.services.cloudwatch.model
            PutMetricDataRequest MetricDatum Dimension
            StandardUnit StatisticSet]
           [com.amazonaws.auth BasicAWSCredentials]
           [com.amazonaws ClientConfiguration ResponseMetadata]))

(defn- kw->unit
  "String or keyword to a StandardUnit AWS-CW enum"
  [unit]
  (StandardUnit/valueOf (name unit)))

(defn create-client ^AmazonCloudWatchClient
  [& {:keys [credentials provider config]}]
  (if-not (or credentials provider config)
    (AmazonCloudWatchClient.)
    (let [config* (or config
                      (doto (ClientConfiguration.)
                        (.withUserAgent "Apollo CW")))]
      (if-not (or credentials provider)
        (AmazonCloudWatchClient. config*)
        (AmazonCloudWatchClient. (or credentials provider) config*)))))

(defn create-async-client ^AmazonCloudWatchAsyncClient
  [& {:keys [credentials provider config]}]
  (if-not (or credentials provider config)
    (AmazonCloudWatchAsyncClient.)
    (let [config* (or config
                      (doto (ClientConfiguration.)
                        (.withUserAgent "Apollo CW")))]
      (if credentials
        (AmazonCloudWatchAsyncClient. credentials)
        (if provider
          (AmazonCloudWatchAsyncClient. provider config*)
          (AmazonCloudWatchAsyncClient. config*))))))

(defn- get-req-id-from-response-meta
  [client request]
  (let [^ResponseMetadata meta-data (.getCachedResponseMetadata client request)]
    (.getRequestId meta-data)))

(defn- client-type
  [client]
  (cond
   (instance? com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient client) :async
   (instance? com.amazonaws.services.cloudwatch.AmazonCloudWatchClient client) :sync))

(defmulti put-metric-data
  (fn [client request]
    (client-type client)))

(defmethod put-metric-data :async
  [^AmazonCloudWatchAsyncClient client  ^PutMetricDataRequest request]
  @(.putMetricDataAsync client request)
  (get-req-id-from-response-meta client request))

(defmethod put-metric-data :sync
  [^AmazonCloudWatchClient client  ^PutMetricDataRequest request]
  (.putMetricData client request)
  (get-req-id-from-response-meta client request))

(defmethod put-metric-data :default
  [client ^PutMetricDataRequest request]
  (throw (ex-info (format "Cannot put metric data for unknown client %s" (str (class client)))
                  {:function "put-metric-data"
                   :client-class (class client)
                   :metric-data (.toString request)}
                  :apollo.error.reason/unknown-client-type)))

(defn gen-stat-set ^StatisticSet
  [{:keys [max min sum count]}]
  (doto (StatisticSet.)
    (.setMaximum (double max))
    (.setMinimum (double min))
    (.setSum (double sum))
    (.setSampleCount (double count))))

(defn gen-dimension ^Dimension
  [dimension-name value]
  (doto (Dimension.)
    (.setName (name dimension-name))
    (.setValue (name value))))

(defn gen-datum ^MetricDatum
  [metric-name & {:keys [dimensions stats value unit timestamp]
                  :or {unit :None timestamp (java.util.Date.)}}]
  (doto (MetricDatum.)
    (.setMetricName (name metric-name))
    (.setValue value)
    (.setUnit (kw->unit unit))
    (.setStatisticValues stats)
    (.setDimensions dimensions)
    (.setTimestamp timestamp)))

(defn gen-put-data-req ^PutMetricDataRequest
  [namespace metric-data & additional]
  (doto (PutMetricDataRequest.)
    (.setNamespace (name namespace))
    (.setMetricData (conj additional metric-data))))
