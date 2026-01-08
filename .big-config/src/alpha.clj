(ns alpha
  (:require
   [big-config :as bc]
   [big-config.render :as render]
   [big-config.run :as run]
   [big-config.step :as step]
   [cheshire.core :as json]
   [clojure.string :as str]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [single :refer [content]]))

(defn run-steps [s opts & step-fns]
  (let [{:keys [_module _profile]} (step/parse-module-and-profile s)
        dir (format "..")
        opts (merge opts
                    {::run/shell-opts {:dir dir
                                       :extra-env {"AWS_PROFILE" "default"}}
                     ::render/templates [{:template "alpha"
                                          :target-dir dir
                                          :overwrite true
                                          :transform [["root"
                                                       :raw]
                                                      ['alpha/render "rama-cluster/single"
                                                       {:single "main.tf.json"}
                                                       :raw]]}]})]
    (if step-fns
      (apply step/run-steps s opts step-fns)
      (step/run-steps s opts))))

(comment
  (run-steps "render exec -- alpha prod bin/rama-cluster.sh plan --singleNode cesar-ford" {::bc/env :repl}))

(defn render [kw _data]
  (case kw
    :single (json/generate-string content {:pretty true})))

(comment
  (do
    (def RamaOptsSchema
      [:map {:closed true}
       [:region [:string {:min 1}]]

       [:cluster_name [:string {:min 1}]]
       [:key_name [:string {:min 1}]]

       [:username [:string {:min 1}]]
       [:vpc_security_group_ids [:+ [:string {:min 1}]]]

       [:rama_source_path [:string {:min 1}]]
       [:license_source_path {:optional true} [:string {:default ""}]]
       [:zookeeper_url [:string {:min 1}]]

       [:ami_id [:string {:min 1}]]

       [:instance_type [:string {:min 1}]]

       [:volume_size_gb {:optional true} [:int {:default 100}]]

       [:use_private_ip {:optional true} [:boolean {:default false}]]

       [:private_ssh_key {:optional true :default nil} [:maybe [:string {:min 1}]]]])

    (def rama-opts
      {:ami_id "ami-0e723566181f273cd"
       :rama_source_path "/Users/amiorin/code/personal/rama-aws-deploy/.cache/rama-1.4.0.zip"
       :cluster_name "cesar-ford"
       :key_name "id_ed25519"
       :instance_type "m6g.medium"
       :username "ec2-user"
       :region "us-west-2"
       :use_private_ip true
       :vpc_security_group_ids ["sg-0e93b1629988a79fd"]
       :zookeeper_url "https://dlcdn.apache.org/zookeeper/zookeeper-3.8.5/apache-zookeeper-3.8.5-bin.tar.gz"})

    (defn malli-assert
      [schema-name schema instance]
      (try
        (->> (m/decode schema instance (mt/default-value-transformer {::mt/add-optional-keys true}))
             (m/assert schema))
        (catch Exception e
          (-> e
              ex-data
              :data
              :explain
              me/humanize
              (->> (reduce-kv (fn [a k v]
                                (let [err (str/join ", " v)]
                                  (if a
                                    (format "%s; `%s` -> `%s`" a k err)
                                    (format "Error in validating %s: `%s` -> `%s`" schema-name k err)))) nil))))))

    (malli-assert "RamaOpts" RamaOptsSchema rama-opts)))
