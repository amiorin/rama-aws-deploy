First read the original [README.md](README-upstream.md)

- [BigConfig](#bigconfig)
- [Customizations](#customizations)
  - [AWS](#aws)
  - [Tailscale](#tailscale)
  - [Variables](#variables)
  - [Caddy](#caddy)

## BigConfig
How to add BigConfig to a Terraform project. The commits of the repo are in the format `step #: [description of the change]`. Start from `step 1` to understand how to add BigConfig to a Terraform project.

### Step 6
Create a BigConfig project in `.big-config` using the Terraform template.

```sh
clojure -Tbig-config terraform :target-dir .big-config
```

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
