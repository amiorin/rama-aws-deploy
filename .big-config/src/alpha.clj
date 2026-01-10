(ns alpha
  (:require
   [big-config :as bc]
   [big-config.core :as core]
   [big-config.utils :as utils]
   [big-config.render :as render]
   [big-config.run :as run]
   [big-config.step :as step]
   [big-config.step-fns :as step-fns]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [single :refer [content content-opts]]))

(defn render [kw _data]
  (case kw
    :single (json/generate-string content {:pretty true})
    :single-opts (json/generate-string content-opts {:pretty true})))

(defn parse-s
  [{:keys [::s] :as opts}]
  (let [{:keys [cluster-name action]} (loop [xs s
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
                                          {:cluster-name token
                                           :action action}))]
    (merge opts {::cluster-name cluster-name
                 ::action action
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

(defn ansible
  [step-fns {:keys [::cluster-name ::bc/env] :as opts}]
  (let [run-steps (step/->run-steps)
        dir "dist"
        ansible-opts {::bc/env env
                      ::step/steps ["render" "exec"]
                      ::run/cmds ["ansible-playbook main.yml"]
                      ::step/module "ansible"
                      ::step/profile cluster-name
                      ::run/shell-opts {:dir dir
                                        :extra-env {"AWS_PROFILE" "default"}}
                      ::render/templates [{:template "ansible"
                                           :data-fn 'alpha/ansible-data-fn
                                           :cluster-name cluster-name
                                           :target-dir dir
                                           :overwrite true
                                           :transform [["root"
                                                        {"projectile" ".projectile"}
                                                        :raw]
                                                       ["root"
                                                        {"inventory.ini" "inventory.ini"}
                                                        :only]]}]}
        ansible-opts (run-steps step-fns ansible-opts)]
    (->> (select-keys ansible-opts [::bc/exit ::bc/err])
         (merge opts {::ansible-opts ansible-opts}))))

(comment
  (ansible nil {::cluster-name "cesar-ford"
                ::bc/env :repl}))

(defn home-path [subpath]
  (io/file (System/getProperty "user.home") subpath))

(defn ansible-data-fn
  [{:keys [cluster-name] :as data} _]
  (let [file-path (-> (format ".rama/%s/outputs.json" cluster-name)
                      home-path)
        rama-ip (-> (json/parse-string (slurp file-path) true)
                    :rama_ip
                    :value)]
    (merge data
           {:rama-ip rama-ip})))

(defn cluster
  [s & opts]
  (let [step-fns [step/print-step-fn
                  (step-fns/->exit-step-fn ::end)
                  (step-fns/->print-error-step-fn ::end)]
        opts (merge {::s s
                     ::bc/env :repl} (first opts))
        rama-cluster (core/->workflow {:first-step ::parse-s
                                       :wire-fn (fn [step step-fns]
                                                  (case step
                                                    ::parse-s [parse-s ::terraform]
                                                    ::terraform [(partial terraform step-fns) ::ansible]
                                                    ::ansible [(partial ansible step-fns) ::end]
                                                    ::end [identity]))
                                       :next-fn (fn [step next-step {:keys [::action] :as opts}]
                                                  (cond
                                                    (= step ::end) [nil opts]
                                                    (and (= step ::terraform)
                                                         (not= action "deploy")) [::end opts]
                                                    :else
                                                    (core/choice {:on-success next-step
                                                                  :on-failure ::end
                                                                  :opts opts})))})]
    (rama-cluster step-fns opts)))

(comment
  (utils/sort-nested-map (cluster "deploy --singleNode cesar-ford" {::bc/env :repl})))
