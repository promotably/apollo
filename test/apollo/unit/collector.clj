(ns apollo.unit.collector
  (:require [apollo.collector :refer :all]
            [midje.sweet :refer :all]))

(background (before :facts (vacuum!)))

(facts "ingest!"
  (ingest! {:ns "namespace"
            :dimensions :dim
            :metric "counter-metric"} 1) => {:min 1 :max 1 :sum 1.0 :count 1}
  (ingest! {:ns "namespace"
            :dimensions :dim
            :metric "counter-metric"} 5) => {:min 1 :max 5 :sum 6.0 :count 2})

(facts "lookup"
  (lookup ["namespace" :dim]) => {:min 6 :max 6 :sum 6.0 :count 1}
  (against-background (before :facts (ingest! {:ns "namespace"
                                               :dimensions :dim} 6))))

(facts "vacuum!"
  (vacuum!) => {"namespace" {:dim {:min 6, :max 6, :sum 6.0, :count 1}}}
  (lookup ["namespace" :dim]) => nil
  (against-background (before :facts (ingest! {:ns "namespace"
                                               :dimensions :dim} 6))))

(facts "current-collection"
  (current-collection) => {"namespace" {:dim {:min 7, :max 7, :sum 7.0, :count 1}}}
  (against-background (before :facts (ingest! {:ns "namespace"
                                               :dimensions :dim} 7))))
