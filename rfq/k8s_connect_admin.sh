#!/bin/sh
admin=$(./k8s_find_admin.sh)
kubectl exec -it $admin -n aeron-io-sample-admin -- java --add-opens java.base/sun.nio.ch=ALL-UNNAMED -jar admin-uber.jar
