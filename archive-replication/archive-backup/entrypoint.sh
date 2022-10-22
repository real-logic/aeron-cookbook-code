#!/bin/sh

java --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  -javaagent:/root/aeron/aeron-agent-1.40.0.jar \
  -Djava.net.preferIPv4Stack=true \
  -Daeron.event.log=admin \
  -Daeron.event.archive.log=all \
  -jar /root/jar/archive-backup-0.1-SNAPSHOT-all.jar
