# Apollo

An AWS Cloudwatch Library for Clojure

## Usage

`[org.promotably.clojars/apollo "0.2.0"]`

and

`[apollo.core :as apollo]`

### Concepts

Cloudwatch limits the number of writes you can make over short periods of time. It's best to keep an in-memory collection of metric data that is vacuumed and sent to Cloudwatch on a periodic basis. This library seeks to do that.

Aggregated metric data is accumulated in-memory and then vacuumed and sent to Cloudwatch in batches.

### Create a Cloudwatch Client

The client creating functions below take these optional keys as arguments:

:credentials AWSCredentials

:provider AWSCredentialsProvider

:config ClientConfiguration

```clojure
(def my-sync-client (apollo/create-cw-client))
(def my-async-client (apollo/create-async-cw-client))
;; or
(def my-sync-client (apollo/create-cw-client :credentials AWSCredentials :config ClientConfiguration))
(def my-sync-client (apollo/create-cw-client :provider AWSCredentialsProvider ...))
```
### Get a metric recorder and record a metric

Metric recording takes place in the context of a namespace and dimensions. You can call the `apollo.core/record!` function directly, supplying your own namespace string and dimension map as the first two of the arguments, or get a recording function returned by `apollo.core/get-context-recorder` which takes a namespace string and dimension map and returns a function wrapped in the namespace and dimensions which can then be called with the metric-name, a value, unit, and optional additional namespace and/or dimension extensions.

#### Example

```clojure
(ns my.things.controller
  (:require [apollo.core :as apollo]))
  
(def ns-metric-recorder (apollo/get-context-recorder "my.things.controller" {:dim-name :value})

;; later...

(ns-metric-recorder "requests" 1 :Count)

;; or

(ns-metric-recorder "requests" 1 :Count :dimensions {:endpoint :endpoint-name})
(ns-metric-recorder "errors" 1 :Count :namespace "errors") ;; passing the optional ns extension will reset dimensions to an empty map.

;; calling record! directly

(apollo/record! "my.namespace.string" {:endpoint "endpoint-name"} "requests" 1 :Count)

```
There are also the following helper functions for simple increment and decrement recording operations:

`(apollo.core/get-context-inc-recorder namespace dimensions)`
`(apollo.core/get-context-dec-recorder namespace dimensions)`

The functions returned by these functions only need to be called with the metric name to increment or decrement.

_The dimensions map in the above examples can be an empty map._

### Enable system metric recording

Simply call `apollo.core/enable-sys-metrics!` with a namespace prefix string as it's only argument. 

### Starting the scheduled vacuum of Metrics

A scheduled executor will vacuum the in-memory collection of aggregated metrics and send them to Cloudwatch on the provided schedule.

Get a ScheduledExecutor:
`(apollo.core/create-vacuum-scheduler)`

Start the scheduler:
`(apollo.core/start-vacuum-scheduler! delay-secs interval-secs scheduler client)`
"client" is your Cloudwatch client...

Stop the scheduler:
`(apollo.core/stop-vacuum-scheduler! scheduler)`

### Apollo Component

For convenience, the `apollo.component` ns provides an implementation of Stuart Sierra's Lifecycle component. Use it directly or as a reference when creating your own. The component will create the Cloudwatch client, & schedule and manage the vacumming of metrics. To work it requires a map of the following form to be passed in when creating a new instance:


```clojure
{:apollo {:sys {:enable? true ;; enables system metric collection
                :ns-prefix "my-app"}
          ;; all of the client options below are optional
          :client {:credentials AWSCredentials
                   :provider AWSCredentialsProvider
                   :config ClientConfiguration
                   ;; or :async
                   :type :sync}
          ;; scheduler config is NOT optional
          :scheduler {:delay 30
                      :interval 30}}}
```

The created client and scheduler instances are accessed via the `client` and `scheduler` properties of the started component.


## TODO:

Make this documentation better and add some docstrings!

## License

Copyright © 2015 Promotably

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
