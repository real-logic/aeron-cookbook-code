#!/bin/sh
clusterdir=$(ls -d */ | cut -f1 -d'/' | grep aeron-cluster | head -1)
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED -cp ~/aeron/aeron-all-*.jar io.aeron.cluster.ClusterTool ~/jar/$clusterdir/cluster describe
