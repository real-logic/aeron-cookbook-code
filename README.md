# Aeron Cookbook Code Samples

Samples which should be reviewed alongside Aeron Cookbook

Projects:
- `sbe-core` + `sbe-protocol` - a project defining an SBE schema, along with some tests showing how to use it.
- `cluster-core` - Aeron cluster samples
- `ipc-core` - focused on IPC samples. Contains the minimal Aeron one file sample, plus a project showing one-way IPC between two agents.
- `eider-spec` - holds the eider specifications used; Eider code is generated externally and copied in to remove any compile time dependency.
- `theory` - examples from the Distributed Systems Basics section 
- `archive-core` - focused on Aeron Archive samples.

Note: JDK 17 requires that `--add-opens java.base/sun.nio.ch=ALL-UNNAMED` is added to the VM Options to run the examples.

Requires:
- Java 17
- Gradle 7.3 RC1 (will move to final once released)

Tested on Ubuntu 20 + macOS 11

 ![build](https://github.com/eleventy7/aeron-cookbook-code/workflows/JavaCI/badge.svg)
 
Sample code from Aeron cookbook.
