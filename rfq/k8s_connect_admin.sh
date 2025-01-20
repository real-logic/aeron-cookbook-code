#!/bin/sh
admin=$(./k8s_find_admin.sh)
kubectl exec -it $admin -n aeron-io-sample-admin -- java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED -jar admin-uber.jar
