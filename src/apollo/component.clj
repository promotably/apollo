(ns apollo.component
  (:require [apollo.core :as apollo]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as c]))

(defrecord ApolloCloudwatch [config client scheduler]
  c/Lifecycle
  (start [this]
    (let [apollo-config            (:apollo config)
          enable-sys?              (-> apollo-config :sys :enable?)
          sys-ns-prefix            (-> apollo-config :sys :ns-prefix)
          credentials              (-> apollo-config :client :credentials)
          provider                 (-> apollo-config :client :provider)
          client-config            (-> apollo-config :client :config)
          client-type              (or (-> apollo-config :client :type) :sync)
          {:keys [delay interval]} (-> apollo-config :scheduler)
          client-fn                (if (= client-type :async)
                                     apollo/create-async-cw-client
                                     apollo/create-cw-client)
          cw-client                (client-fn :credentials credentials
                                              :provider provider
                                              :config client-config)
          executor                 (apollo/create-vacuum-scheduler)]
      (when enable-sys?
        (apollo/enable-sys-metrics! sys-ns-prefix))
      (apollo/start-vacuum-scheduler! (or delay 1) (or interval 1) executor cw-client)
      (log/info "Apollo vacuum scheduler started.")
      (log/info "Apollo Cloudwatch component started.")
      (assoc this :client cw-client :scheduler executor)))
  (stop [this]
    (when scheduler
      (do (apollo/stop-vacuum-scheduler! scheduler)
          (log/info "Apollo vacuum scheduler stopped.")))
    (log/info "Apollo Cloudwatch component stopped.")
    (assoc this :client nil :scheduler nil)))
