(ns alpha
  (:require
   [big-config :as bc]
   [big-config.render :as render]
   [big-config.run :as run]
   [big-config.step :as step]
   [big-config.step-fns :as step-fns]
   [cheshire.core :as json]
   [clojure.string :as str]
   [single :refer [content content-opts]]))

(defn render [kw _data]
  (case kw
    :single (json/generate-string content {:pretty true})
    :single-opts (json/generate-string content-opts {:pretty true})))

(defn cluster
  [s & opts]
  (let [opts (first opts)
        {:keys [cluster-name]} (loop [xs s
                                      token nil
                                      action nil
                                      single false]
                                 (cond
                                   (string? xs)
                                   (let [xs (-> (str/trim xs)
                                                (str/split #"\s+"))]
                                     (recur (rest xs) (first xs) action single))

                                   (#{"deploy" "destroy" "plan"} token)
                                   (recur (rest xs) (first xs) token single)

                                   (#{"--singleNode"} token)
                                   (recur (rest xs) (first xs) action true)

                                   :else
                                   {:action action
                                    :single single
                                    :cluster-name token
                                    :terraform-opts xs}))
        run-steps (step/->run-steps)
        [_ last-step] (run-steps)
        step-fns [step/print-step-fn
                  (step-fns/->exit-step-fn last-step)
                  (step-fns/->print-error-step-fn last-step)]
        dir ".."
        opts (merge (or opts {})
                    {::step/steps ["render" "exec"]
                     ::run/cmds [(format "bin/rama-cluster.sh %s" s)]
                     ::step/module "terraform"
                     ::step/profile cluster-name
                     ::run/shell-opts {:dir dir
                                       :extra-env {"AWS_PROFILE" "default"}}
                     ::render/templates [{:template "alpha"
                                          :target-dir dir
                                          :overwrite true
                                          :data-fn (constantly content-opts)
                                          :transform [["root"]
                                                      ['alpha/render "rama-cluster/single"
                                                       {:single "main.tf.json"}
                                                       :raw]
                                                      ['alpha/render
                                                       {:single-opts "rama.tfvars.json"}
                                                       :raw]]}]})]
    (run-steps step-fns opts)))

(comment
  (cluster "deploy --singleNode cesar-ford" {::bc/env :repl}))
