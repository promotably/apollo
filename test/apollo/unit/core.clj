(ns apollo.unit.core
  (:require [apollo.core :as apollo]
            [apollo.cloudwatch :as cw]
            [apollo.collector :as collector]
            [midje.sweet :refer :all]))

(background (before :facts (collector/vacuum!)))

(defchecker datum-for [n]
  (checker [actual] (re-matches (re-pattern (name n)) (str (.getMetricName actual)))))

(facts "Metrics recorded"
  ((apollo/get-context-recorder ".dev" {:Dimen :sion}) "name" 10.0 :Seconds) => anything
  (provided
    (collector/ingest! {:ns ".dev"
                        :dimensions {:Dimen "sion"}
                        :metric "name"
                        :unit :Seconds} 10.0) => ..anything..))

(facts "Counters increment"
  ((apollo/get-context-inc-recorder ".dev" {:Dimen :sion}) "name") => anything
  (provided
    (collector/ingest! {:ns ".dev"
                       :dimensions {:Dimen "sion"}
                       :metric "name"
                       :unit :Count} 1.0) => ..anything..))

(facts "Namespaces are appended"
  ((apollo/get-context-inc-recorder ".dev" {}) "things" :namespace "test") => ..anything..
  (provided
    (collector/ingest! {:ns ".dev.test"
                        :dimensions {}
                        :metric "things"
                        :unit :Count} 1.0) => ..anything..))

(facts "Dimensions are appended"
  ((apollo/get-context-recorder ".dev" {:Dimen :sion}) "things" 10.0 :Seconds :dimensions {:Other :dim}) => ..anything..
  (provided
    (collector/ingest! {:ns ".dev"
                        :dimensions {:Dimen "sion"
                                     :Other "dim"}
                        :metric "things"
                        :unit :Seconds} 10.0) => ..anything..))

(facts "Dimensions context cleared when optional ns is provided"
  ((apollo/get-context-recorder ".dev" {:Dimen :sion}) "stuff" 10.0 :Seconds :namespace "test") => ..anything..
  (provided
    (collector/ingest! {:ns ".dev.test"
                        :dimensions {}
                        :metric "stuff"
                        :unit :Seconds} 10.0) => ..anything..))

(facts "Parses collection into datum map"
  (let [collection {".ns0" {{:Name "John"} {"Alive"  {:Seconds {:min 0 :max 0 :sum 0 :count 1}}
                                            "Breaths"{:Count {:min 0 :max 0 :sum 0 :count 2}}}}
                    ".ns1" {{} {"Errors" {:Count {:min 0 :max 0 :sum 0 :count 3}}}}}]
    (apollo/vacuum! ..client..) => (just ["..this0.." "..this1.."] :in-any-order)
    (provided
      (collector/vacuum!) => collection
      (cw/gen-put-data-req "ns0" (datum-for "Alive|Breaths") (datum-for "Breaths|Alive"))
      => ..req0..
      (cw/gen-put-data-req "ns1" (datum-for "Errors")) => ..req1..
      (cw/put-metric-data ..client.. ..req0..) => "..this0.."
      (cw/put-metric-data ..client.. ..req1..) => "..this1..")))
