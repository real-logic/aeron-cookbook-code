#!/bin/sh

java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED \
 -Djava.net.preferIPv4Stack=true \
 -jar /root/jar/archive-host-uber.jar
