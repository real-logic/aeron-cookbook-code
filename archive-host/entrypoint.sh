#!/bin/sh

echo "host:" $ARCHIVEHOST
echo "control port:" $CONTROLPORT
echo "events port:" $EVENTSPORT

java --illegal-access=permit -jar /root/jar/archive-host-0.1-SNAPSHOT-all.jar
