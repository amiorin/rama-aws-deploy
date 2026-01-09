(ns alpha
  (:require
   [big-config :as bc]
   [big-config.core :as core]
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

(defn s->cluster-name
  [{:keys [::s] :as opts}]
  (let [cluster-name (loop [xs s
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
                         token))]
    (merge opts {::cluster-name cluster-name
                 ::bc/exit 0
                 ::bc/err nil})))

(defn terraform
  [step-fns {:keys [::s ::cluster-name ::bc/env] :as opts}]
  (let [run-steps (step/->run-steps)
        dir ".."
        terraform-opts {::bc/env env
                        ::step/steps ["render" "exec"]
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
                                                          :raw]]}]}
        terraform-opts (run-steps step-fns terraform-opts)]
    (->> (select-keys terraform-opts [::bc/exit ::bc/err])
         (merge opts {::terraform-opts terraform-opts}))))

(defn cluster
  [s & opts]
  (let [run-steps (step/->run-steps)
        [_ last-step] (run-steps)
        step-fns [step/print-step-fn
                  (step-fns/->exit-step-fn last-step)
                  (step-fns/->print-error-step-fn last-step)]
        opts (merge {::s s
                     ::bc/env :repl} (first opts))
        rama-cluster (core/->workflow {:first-step ::s->cluster-name
                                       :wire-fn (fn [step step-fns]
                                                  (case step
                                                    ::s->cluster-name [s->cluster-name ::terraform]
                                                    ::terraform [(partial terraform step-fns) ::ansible]
                                                    ::ansible [(fn [opts]
                                                                 (println "ansible")
                                                                 (core/ok opts)) ::end]
                                                    ::end [identity]))})]
    (rama-cluster step-fns opts)))

(comment
  (cluster "plan --singleNode cesar-ford" {::bc/env :repl}))
