(ns alpha
  (:require
   [big-config :as bc]
   [big-config.lock :as lock]
   [big-config.render :as render]
   [big-config.run :as run]
   [big-config.step :as step]
   [big-config.utils :refer [deep-merge sort-nested-map]]
   [big-tofu.core :refer [add-suffix construct]]
   [big-tofu.create :as create]
   [cheshire.core :as json]))

(defn run-steps [s opts & step-fns]
  (let [{:keys [module profile]} (step/parse-module-and-profile s)
        dir (format ".." profile module)
        opts (merge opts
                    {::run/shell-opts {:dir dir
                                       :extra-env {"AWS_PROFILE" "default"}}
                     ::render/templates [{:template "alpha"
                                          :target-dir dir
                                          :overwrite true
                                          :transform [["root"
                                                       :raw]]}]})]
    (if step-fns
      (apply step/run-steps s opts step-fns)
      (step/run-steps s opts))))

(comment
  (run-steps "render exec -- alpha prod bin/rama-cluster.sh plan --singleNode cesar-ford" {::bc/env :repl}))
