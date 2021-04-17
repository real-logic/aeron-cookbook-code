# Aeron Cookbook Code Samples

Samples which should be reviewed alongside Aeron Cookbook

Warning: The sample code is in the process of being updated for the next release of Aeron, and is run off a locally built snapshot of Aeron. This will not compile unless you publish Aeron master to local Maven first. As an alternate, please use the last Aeron 1.32.0 compatible commit `d1e1170e648bdd48aac8e6626817a86ed91daf85`.

Projects:
- `sbe-core` + `sbe-protocol` - a project defining an SBE schema, along with some tests showing how to use it.
- `cluster-core` - Aeron cluster samples
- `ipc-core` - focused on IPC samples. Contains the minimal Aeron one file sample, plus a project showing one-way IPC between two agents.
- `eider-spec` - holds the eider specifications used; Eider code is generated externally and copied in to remove any compile time dependency.
- `theory` - examples from the Distributed Systems Basics section 
- `archive-core` - focused on Aeron Archive samples.

Note: JDK 16+ requires that `--illegal-access=permit` is added to the VM Options to run the examples.

Requires:
- Java 16
- Gradle 7

Tested on Ubuntu 20 + macOS 11

 ![build](https://github.com/eleventy7/aeron-cookbook-code/workflows/JavaCI/badge.svg)
 
Sample code from Aeron cookbook.
