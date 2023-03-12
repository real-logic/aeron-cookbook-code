#!/bin/sh
clusterdir=$(ls -d */ | cut -f1 -d'/' | grep aeron-cluster | head -1)
java -cp ~/aeron/aeron-all-*.jar io.aeron.cluster.ClusterTool ~/jar/$clusterdir/cluster errors
