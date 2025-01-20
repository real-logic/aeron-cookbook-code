#!/bin/sh

java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -javaagent:/root/aeron/aeron-agent-1.47.0.jar \
  -Daeron.event.log=admin \
  -Djava.net.preferIPv4Stack=true \
  -jar /root/jar/aeron-mdc-publisher-uber.jar
