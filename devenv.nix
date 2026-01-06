{ pkgs, lib, config, inputs, ... }:

{
  languages.clojure.enable = true;
  languages.terraform.enable = true;
  packages = [
    pkgs.jet
    pkgs.hcl2json
  ];
}
