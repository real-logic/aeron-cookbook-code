# Cluster Protocol

Project to hold the definition of the cluster protocol for the Auction.
All Java code in this project is generated, and should not be directly edited.

See `/src/java/main/resources/protocol/protocol-codecs.xml` for the protocol definition.

To generate the Java sources from the SBE definitions, run:

```bash
./gradlew generateCodecs
```

`generateCodecs` uses the SBE tool to generate the Java sources.
See `build.gradle.kts` for the task definition on how to configure and use SBE Tool with gradle.

Generated files can be found in `build/generated/src/main/java`.