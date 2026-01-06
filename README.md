First read the original [README.md](README-upstream.md)

- [BigConfig](#bigconfig)
  - [Requirements](#requirements)
  - [Step 1-5](#step-1-5)
  - [Step 6](#step-6)
  - [Step 7](#step-7)
  - [Step 8](#step-8)
- [Customizations](#customizations)
  - [AWS](#aws)
  - [Tailscale](#tailscale)
  - [Variables](#variables)
  - [Caddy](#caddy)

## BigConfig
How to add BigConfig to a Terraform project. The commits of the repo are in the format `step #: [description of the change]`. Start from `step 1` to understand how to add BigConfig to a Terraform project.

### Requirements
* babashka 1.12.210 or above (from asdf or brew for example, nix lastest version is only 1.12.209)

### Step 1-5
Preparation steps.

### Step 6
Create a BigConfig project in `.big-config` using the Terraform template.

```sh
clojure -Tbig-config terraform :target-dir .big-config
```

### Step 7
There is a bug in the Terraform template. The directory `resources/alpha/` is not created automatically. Even if the template is only using functions to generate configuration files, the directory is required anyway.

* add `rama-cluster/single/main.tf` inside `resources/alpha` to the list of configuration files. For now it is copied verbatim. Later it will be transform into a function.
* remove the lock properties for now.
* remove `data-fn` and `kw->content` for now. The `main.tf` is just copied verbatim.
* add a proxy `bb.edn` to be able to invoke `.big-config/bb.edn` from the root of the project.
* make the `transform` just a verbatim copy of the `root` folder.
* change the `target-dir` to be the parent directory of `.big-config`.

```sh
# from
bin/rama-cluster.sh plan --singleNode cesar-ford

# to
bb render exec -- alpha prod bin/rama-cluster.sh plan --singleNode cesar-ford

# Using the alias to achieve zero-cost build step (https://bigconfig.it/start-here/getting-started/#zero-cost-build-step)
alias rama-cluster="bb render exec -- alpha prod bin/rama-cluster.sh"
```

### Step 8
Now we can convert the `main.tf` to [single.clj](.big-config/src/single.clj)

```sh
cat  rama-cluster/single/main.tf | hcl2json | jet --from json --to edn --pretty --keywordize > .big-config/src/single.clj
```

Then we can remove the old `main.tf` and generate the `main.tf.json` programmatically. The conversion from HCL to EDN is no bullet proof and Terraform will give us some syntax errors that we will fix in step 9. After we have a working version of `main.tf.json` we will be able to refactor without running Terraform anymore.

## Customizations
These are the steps if you want to use AWS, Tailscale, SSH Agent, and Caddy with Rama.

### AWS
* Create AWS user with admin rights
* Enable MFA
* Create access key and save it in ~/.aws/credentials

### Tailscale
* Create a t4g.nano
* Open (0.0.0.0/0) these security group ports: UDP:41641, ICMP, TCP:22
* Install tailscale
* Enable ip forwarding
* `sudo tailscale set --advertise-routes=172.31.0.0/20,172.31.32.0/20,172.31.48.0/20,172.31.16.0/20`
* Accept the subnets in the Tailscale console
* Stop "Change Source / destination check" of the t4g.nano

### Variables
```hcl
# Required
region                 = "us-west-2"
username               = "ec2-user"
vpc_security_group_ids = ["sg-0e93b1629988a79fd"]

# You need to download and create this directory manually
rama_source_path = "/Users/amiorin/.rama/cache/rama-1.4.0.zip"
zookeeper_url    = "https://dlcdn.apache.org/zookeeper/zookeeper-3.8.5/apache-zookeeper-3.8.5-bin.tar.gz"

# Amazon Linux 2023 for ARM
ami_id = "ami-0e723566181f273cd"

instance_type = "m6g.medium"

# Optional
# This has to be an empty string
license_source_path = ""
volume_size_gb      = 100
use_private_ip      = true
# This has to be null
# private_ssh_key = ""
```

### Caddy
Given the ip address 172.31.43.17 and the cluster name `cesar-ford`, you can install the monitoring software and access it through Caddy. `~/.rama` must be in your path.

```sh
rama-cesar-ford deploy --action launch --systemModule monitoring --tasks 4 --threads 2 --workers 1
caddy reverse-proxy --to http://172.31.43.17:8888
open -a "Google Chrome" https://localhost
```
