(ns apollo.unit.cloudwatch
  (:require [apollo.cloudwatch :refer :all]
            [midje.sweet :refer :all])
  (:import (com.amazonaws.auth BasicAWSCredentials)))

(facts "Client instantiates"
  (type (create-client :credentials (BasicAWSCredentials. "key" "secret")))
  => com.amazonaws.services.cloudwatch.AmazonCloudWatchClient)

(facts "Async client instantiates"
  (type (create-async-client :credentials (BasicAWSCredentials. "key" "secret")))
  => com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient)

(facts "Can gen StatisticSet"
  (type (gen-stat-set {:max 12.0 :min 10.0 :sum 22.0 :count 2}))
  => com.amazonaws.services.cloudwatch.model.StatisticSet)

(facts "Can gen Dimension"
  (type (gen-dimension "Something" "Happened"))
  => com.amazonaws.services.cloudwatch.model.Dimension
  (type (gen-dimension :Something :Happened))
  => com.amazonaws.services.cloudwatch.model.Dimension)

(facts "Can gen MetricDatum"
  (type (gen-datum "something" :value 25.0 :unit "Seconds"))
  => com.amazonaws.services.cloudwatch.model.MetricDatum
  (type (gen-datum "else" :value 25.0 :unit :CountSecond))
  => com.amazonaws.services.cloudwatch.model.MetricDatum
  (let [dimensions [(gen-dimension :Something :Happened)]]
    (type (gen-datum "test" :value 25.0 :unit "Seconds" :dimensions dimensions))
    => com.amazonaws.services.cloudwatch.model.MetricDatum)
  (let [stats (gen-stat-set {:max 12 :min 10 :sum 22 :count 2})]
    (type (gen-datum "test" :unit "Seconds" :stats stats))
    => com.amazonaws.services.cloudwatch.model.MetricDatum))

(facts "Can gen PutMetricDataRequest"
  (type (gen-put-data-req "NS"{}))
  => com.amazonaws.services.cloudwatch.model.PutMetricDataRequest)
