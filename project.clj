(defproject org.clojars.promotably/apollo "0.2.4"
  :description "A clojure library for Amazon Cloudwatch"
  :url "http://github.com/promotably/apollo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.amazonaws/aws-java-sdk "1.9.23"
                  :exclusions [joda-time]]
                 [com.stuartsierra/component "0.2.3"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [org.slf4j/slf4j-api "1.7.10"]
                                  [org.slf4j/jcl-over-slf4j "1.7.10"]
                                  [org.slf4j/slf4j-log4j12 "1.7.10"]
                                  [log4j/log4j "1.2.17"]]}})
