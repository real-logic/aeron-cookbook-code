# Aeron Cookbook Code Samples

Samples which should be reviewed alongside Aeron Cookbook 

Projects:
- `sbe-core` + `sbe-protocol` - a project defining an SBE schema, along with some tests showing how to use it.
- `cluster-core` - Aeron cluster samples
- `ipc-core` - focused on IPC samples. Contains the minimal Aeron one file sample, plus a project showing one-way IPC between two agents.
- `eider-spec` - holds the eider specifications used; Eider code is generated externally and copied in to remove any compile time dependency.
- `theory` - examples from the Distributed Systems Basics section 
- `archive-core` - focused on Aeron Archive samples.

Requires:
- Java 16
- Gradle 7 RC 1 (to support Java 16; will switch to release once published)

Tested on Ubuntu 20 + macOS 11

 ![build](https://github.com/eleventy7/aeron-cookbook-code/workflows/JavaCI/badge.svg)
 
Sample code from Aeron cookbook.
