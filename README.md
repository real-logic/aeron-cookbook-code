# Aeron Cookbook Code Samples

Samples which should be reviewed alongside Aeron Cookbook 

Projects:
- `sbe-core` + `sbe-protocol` - a project defining an SBE schema, along with some tests showing how to use it.
- `cluster-core` - Aeron cluster samples
- `ipc-core` - focused on IPC samples. Contains the minimal Aeron one file sample, plus a project showing one-way IPC between two agents.
- `eider-protocol` - holds the eider specifications used in other projects
- `theory` - examples from the Distributed Systems Basics section 
- `archive-core` - focused on Aeron Archive samples.

Requires:
- Java 11
- Eider
- Gradle 6.6.1

Tested on Ubuntu 18 & macOS 10.15

 ![build](https://github.com/adaptive-sl/aeron-cookbook-code/workflows/JavaCI/badge.svg)
 
 Work in progress Aeron cookbook.
 
 Eider sits on GitHub packages for now. You will need to create a file `eider.properties` to gain access. Add the following:
 
`gpr.usr=your-username`
`gpr.key=personal-access-token-with-repository-read`

[See GitHub for details on the personal access token](https://help.github.com/en/packages/using-github-packages-with-your-projects-ecosystem/configuring-gradle-for-use-with-github-packages#authenticating-to-github-packages)