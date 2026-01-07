First read the original [README.md](README-upstream.md)

- [BigConfig](#bigconfig)
  - [Requirements](#requirements)
  - [Step 1-5](#step-1-5)
  - [Step 6](#step-6)
  - [Step 7](#step-7)
    - [Tasks](#tasks)
    - [Command updates](#command-updates)
  - [Step 8](#step-8)
  - [Step 9](#step-9)
- [Customizations](#customizations)
  - [AWS](#aws)
  - [Tailscale](#tailscale)
  - [Variables Configuration (.tfvars / hcl)](#variables-configuration-tfvars--hcl)
  - [Caddy \& Monitoring](#caddy--monitoring)

## BigConfig
This guide explains how to integrate BigConfig into a Terraform project. The repository commits follow the format `step #: [description of the change]`. To understand the integration process, follow the steps sequentially starting from Step 1.

### Requirements
* Babashka 1.12.210 or above (available via asdf or brew; note that the latest Nix version is currently 1.12.209).

### Step 1-5
Initial preparation.

### Step 6
Create a BigConfig project within the `.big-config` directory using the Terraform template:

```sh
clojure -Tbig-config terraform :target-dir .big-config
```

### Step 7
The current Terraform template contains a minor bug where the `resources/alpha/` directory is not created automatically. Even when using functions to generate configuration files, this directory is required.

#### Tasks

* Add `rama-cluster/single/main.tf` to the list of configuration files inside `resources/alpha`. For now, this file is copied verbatim; it will be functionalized in a later step.

* Remove the lock properties for now.

* Remove `data-fn` and `kw->content` temporarily, as main.tf is currently being copied verbatim.

* Add a proxy `bb.edn` to allow invoking `.big-config/bb.edn` from the project root.

* Configure the transform step to act as a verbatim copy of the `root` folder.

* Update the `target-dir` to be the parent directory of `.big-config`.

#### Command updates

```sh
# From:
bin/rama-cluster.sh plan --singleNode cesar-ford

# To:
bb render exec -- alpha prod bin/rama-cluster.sh plan --singleNode cesar-ford

# Using an alias to achieve a zero-cost build step: (https://bigconfig.it/start-here/getting-started/#zero-cost-build-step)
alias rama-cluster="bb render exec -- alpha prod bin/rama-cluster.sh"
```

### Step 8
Convert the `main.tf` file into [single.clj](.big-config/src/single.clj):

```sh
cat  rama-cluster/single/main.tf | hcl2json | jet --from json --to edn --pretty --keywordize > .big-config/src/single.clj
```

Once converted, delete the original `main.tf` and generate the `main.tf.json` programmatically.

> Note: The conversion from HCL to EDN is not infallible. Terraform may report syntax errors, which will be addressed in Step 9. Once `main.tf.json` is fully functional, you can perform refactoring without needing to run Terraform repeatedly.

### Step 9
Resolve the syntax errors introduced during the HCL-to-EDN conversion. Once fixed, the build will be "green" (successful) again.

## Customizations
Follow these steps to configure AWS, Tailscale, SSH Agent, and Caddy for use with Rama.

### AWS
* Create AWS user with AdministratorAccess.
* Enable MFA.
* Generate an Access Key and save it to `~/.aws/credentials`.

### Tailscale
* Provision a `t4g.nano` instance.
* Open the following ports in the Security Group (0.0.0.0/0): UDP:41641, ICMP, and TCP:22.
* Install Tailscale on the instance.
* Enable ip forwarding.
* Advertise routes:
``` sh
sudo tailscale set --advertise-routes=172.31.0.0/20,172.31.32.0/20,172.31.48.0/20,172.31.16.0/20
```
* Accept the subnets in the Tailscale admin console.
* Disable "Source/Destination Check" for the t4g.nano instance in AWS.

### Variables Configuration (.tfvars / hcl)
```hcl
# Required Variables
region                 = "us-west-2"
username               = "ec2-user"
vpc_security_group_ids = ["sg-0e93b1629988a79fd"]

# Manual Setup Required:
# Ensure this directory exists and contains the zip file.
rama_source_path = "/Users/amiorin/.rama/cache/rama-1.4.0.zip"
zookeeper_url    = "https://dlcdn.apache.org/zookeeper/zookeeper-3.8.5/apache-zookeeper-3.8.5-bin.tar.gz"

# Amazon Linux 2023 (ARM)
ami_id = "ami-0e723566181f273cd"

instance_type = "m6g.medium"

# Optional Variables
license_source_path = "" # Must be an empty string if not used
volume_size_gb      = 100
use_private_ip      = true
# private_ssh_key = "" # Set to null if not using a specific key
```

### Caddy & Monitoring
With the IP address `172.31.43.17` and cluster name `cesar-ford`, you can deploy the monitoring suite and access it via Caddy. Ensure `~/.rama` is in your system PATH.

```sh
rama-cesar-ford deploy --action launch --systemModule monitoring --tasks 4 --threads 2 --workers 1
caddy reverse-proxy --to http://172.31.43.17:8888
open -a "Google Chrome" https://localhost
```
