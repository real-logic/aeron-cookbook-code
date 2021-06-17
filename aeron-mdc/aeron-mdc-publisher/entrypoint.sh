#!/bin/sh

java --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  -javaagent:/root/aeron/aeron-agent-1.33.1.jar \
  -Djava.net.preferIPv4Stack=true \
  -Daeron.event.log=admin \
  -jar /root/jar/aeron-mdc-publisher-0.1-SNAPSHOT-all.jar
