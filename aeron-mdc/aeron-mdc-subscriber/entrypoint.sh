#!/bin/sh

java --add-opens java.base/sun.nio.ch=ALL-UNNAMED -Djava.net.preferIPv4Stack=true -jar /root/jar/aeron-mdc-subscriber-0.1-SNAPSHOT-all.jar
