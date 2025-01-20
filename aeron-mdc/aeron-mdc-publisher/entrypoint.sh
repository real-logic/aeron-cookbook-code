#!/bin/sh

java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -javaagent:/root/aeron/aeron-agent-1.47.0.jar \
  -Djava.net.preferIPv4Stack=true \
  -Daeron.event.log=admin \
  -jar /root/jar/aeron-mdc-publisher-uber.jar
