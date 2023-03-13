#!/bin/sh
leader=$(./k8s_find_leader.sh)
echo Connecting to leader $leader ...
kubectl exec -it $leader -n aeron-io-sample-cluster -- /bin/sh
