#!/bin/sh

java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -javaagent:/root/aeron/aeron-agent-1.47.0.jar \
  -Daeron.event.log=admin \
  -Daeron.event.archive.log=all \
  -Djava.net.preferIPv4Stack=true \
  -jar /root/jar/archive-backup-uber.jar
