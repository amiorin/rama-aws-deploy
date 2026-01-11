(ns alpha
  (:require
   [babashka.process :as p]
   [big-config :as bc]
   [big-config.core :as core]
   [big-config.render :as render]
   [big-config.run :as run]
   [big-config.step :as step]
   [big-config.step-fns :as step-fns]
   [big-config.utils :as utils]
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
  (let [all-actions #{"deploy" "destroy" "plan" "ansible"}
        args (loop [xs s
                    token nil
                    actions []
                    single false
                    cluster-name nil
                    terraform-args []
                    ansible-args-separator false
                    ansible-args []]
               (cond
                 (string? xs)
                 (let [xs (-> (str/trim xs)
                              (str/split #"\s+"))]
                   (recur (rest xs) (first xs) actions single cluster-name terraform-args ansible-args-separator ansible-args))

                 (all-actions token)
                 (let [actions (into actions [token])]
                   (recur (rest xs) (first xs) actions single cluster-name terraform-args ansible-args-separator ansible-args))

                 (nil? (seq actions))
                 (throw (ex-info (format "At least one action must be defined (%s)" (str/join "|" actions)) {:opts opts}))

                 (#{"--singleNode"} token)
                 (recur (rest xs) (first xs) actions true cluster-name terraform-args ansible-args-separator ansible-args)

                 (all-actions token)
                 (throw (ex-info "Actions must be defined before `--singleNone`" {:opts opts}))

                 (nil? cluster-name)
                 (recur (rest xs) (first xs) actions single token terraform-args ansible-args-separator ansible-args)

                 (= "--" token)
                 (recur (rest xs) (first xs) actions single cluster-name terraform-args true ansible-args)

                 (and token (not ansible-args-separator))
                 (let [terraform-args (into terraform-args [token])]
                   (recur (rest xs) (first xs) actions single cluster-name terraform-args ansible-args-separator ansible-args))

                 (and token ansible-args-separator)
                 (let [ansible-args (into ansible-args [token])]
                   (recur (rest xs) (first xs) actions single cluster-name terraform-args ansible-args-separator ansible-args))

                 :else
                 {::cluster-name cluster-name
                  ::actions actions
                  ::single single
                  ::terraform-args terraform-args
                  ::ansible-args ansible-args}))]
    (merge opts args {::bc/exit 0
                      ::bc/err nil})))

(comment
  (parse-s {::s "destroy plan deploy ansible --singleNode cesar-ford -- --tags focus"}))

(defn terraform-cmd
  [{:keys [::cluster-name
           ::action
           ::single
           ::terraform-args]}]
  (as-> ["bin/rama-cluster.sh"] $
    (conj $ action)
    (cond-> $
      single (conj "--singleNode"))
    (conj $ cluster-name)
    (into $ terraform-args)
    (str/join " " $)))

(comment
  (terraform-cmd {::cluster-name "cesar-ford"
                  ::action "plan"
                  ::single true
                  ::terraform-args ["foo" "bar"]}))

(defn terraform
  [step-fns {:keys [::cluster-name
                    ::bc/env] :as opts}]
  (let [run-steps (step/->run-steps)
        dir ".."
        terraform-opts {::bc/env env
                        ::step/steps ["render" "exec"]
                        ::run/cmds [(terraform-cmd opts)]
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
  [step-fns {:keys [::cluster-name ::bc/env ::ansible-args] :as opts}]
  (let [run-steps (step/->run-steps)
        dir "dist"
        ansible-opts {::bc/env env
                      ::step/steps ["render" "exec"]
                      ::run/cmds [(format "ansible-playbook main.yml %s" (str/join " " ansible-args))]
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

(defn rama-ip
  [cluster-name]
  (let [file-path (-> (format ".rama/%s/outputs.json" cluster-name)
                      home-path)]
    (-> (json/parse-string (slurp file-path) true)
        :rama_ip
        :value)))

(defn ansible-data-fn
  [{:keys [cluster-name] :as data} _]
  (merge data
         {:rama-ip (rama-ip cluster-name)}))

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
                                                    ::parse-s [parse-s ::any]
                                                    ::terraform [(partial terraform step-fns) ::any]
                                                    ::ansible [(partial ansible step-fns) ::any]
                                                    ::end [identity]))
                                       :next-fn (fn [step _ {:keys [::bc/exit ::actions] :as opts}]
                                                  (cond
                                                    (= step ::end) [nil opts]

                                                    (and (#{"plan" "deploy" "destroy"}
                                                          (first actions))
                                                         (= exit 0))
                                                    [::terraform (merge opts {::actions (rest actions)
                                                                              ::action (first actions)})]

                                                    (and (#{"ansible"}
                                                          (first actions))
                                                         (= exit 0))
                                                    [::ansible (merge opts {::actions (rest actions)
                                                                            ::action (first actions)})]

                                                    :else [::end opts]))})]
    (rama-cluster step-fns opts)))

(comment
  (utils/sort-nested-map (cluster "destroy ansible --singleNode cesar-ford --tags focus" {::bc/env :repl})))

(defn ssh
  [[cluster-name]]
  (p/shell (format "ssh ec2-user@%s" (rama-ip cluster-name))))
