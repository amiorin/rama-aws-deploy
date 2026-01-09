(ns multi
  (:require
   [big-config :as bc]
   [big-config.render :as render]
   [big-config.run :as run]
   [big-config.step :as step]
   [big-config.utils :refer [deep-merge sort-nested-map]]
   [big-tofu.core :refer [add-suffix construct]]
   [big-tofu.create :as create]
   [cheshire.core :as json]))

(defn run-steps [s opts & step-fns]
  (let [{:keys [module profile]} (step/parse-module-and-profile s)
        dir (format "dist/%s/%s" profile module)
        opts (merge opts
                    {::run/shell-opts {:dir dir
                                       :extra-env {"AWS_PROFILE" "default"}}
                     ::render/templates [{:template "multi"
                                          :data-fn 'multi/data-fn
                                          :target-dir dir
                                          :overwrite true
                                          :transform [["common"
                                                       {"projectile" ".projectile"}
                                                       :raw]
                                                      ["gamma"
                                                       :raw]
                                                      ["gamma"
                                                       {"inventory.ini" "inventory.ini"}]]}]})]
    (if step-fns
      (apply step/run-steps s opts step-fns)
      (step/run-steps s opts))))

(comment
  (run-steps "render -- alpha prod" {::bc/env :repl}))

(defn data-fn
  [{:keys [profile] :as data} _]
  (let [file-path "/Users/amiorin/.rama/cesar-ford/outputs.json"
        rama-ip (-> (json/parse-string (slurp file-path) true)
                    :rama_ip
                    :value)]
    (merge data
           {:rama-ip rama-ip
            :region "eu-west-1"
            :aws-account-id (case profile
                              "dev" "111111111111"
                              "prod" "222222222222")})))

(defn kw->content
  [kw {:keys [region aws-account-id] :as data}]
  (case kw
    :beta (let [queues (->> (for [n (range 2)]
                              (create/sqs (add-suffix :alpha/big-sqs (str "-" n))))
                            flatten
                            (map construct))
                kms (->> (create/kms :alpha/big-kms)
                         (map construct))
                bucket (format "tf-state-%s-%s" aws-account-id region)
                provider (create/provider (assoc data :bucket bucket))
                m (->> [provider]
                       (into kms)
                       (into queues)
                       (apply deep-merge)
                       sort-nested-map)]
            (json/generate-string m {:pretty true}))))
