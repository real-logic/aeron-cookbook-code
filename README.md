# Aeron Cookbook Code Samples

Samples which should be reviewed alongside Aeron Cookbook 

Projects:
- `sbe-core` + `sbe-protocol` - a project defining an SBE schema, along with some tests showing how to use it.
- `cluster-core` - minimal single file Aeron cluster
- `ipc-core` - focused on IPC samples. Contains the minimal Aeron one file sample, plus a project showing one-way IPC between two agents.
- `archive-core` - focused on Aeron Archive samples.

Requires:
- Java 11
- Gradle 6

Uses Eider in projects other than the SBE projects. You will need to [setup gradle to get the dependencies](https://help.github.com/en/packages/using-github-packages-with-your-projects-ecosystem/configuring-gradle-for-use-with-github-packages)

Tested on Ubuntu 18 & macOS 10.15

 ![build](https://github.com/adaptive-sl/aeron-cookbook-code/workflows/JavaCI/badge.svg)
 
 Work in progress Aeron cookbook.