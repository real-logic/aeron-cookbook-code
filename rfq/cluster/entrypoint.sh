#!/bin/sh
java -Djava.net.preferIPv4Stack=true -Daeron.ipc.mtu.length=8k "$@" -jar /root/jar/cluster-uber.jar
