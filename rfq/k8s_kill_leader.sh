#!/bin/sh
leader=$(./k8s_find_leader.sh)
echo Killing $leader ... this may take kubernetes a few minutes...
kubectl delete pod $leader -n aeron-io-sample-cluster
