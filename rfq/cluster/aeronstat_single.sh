#!/bin/sh
aerondir=$(ls /dev/shm/ | grep aeron | head -1)
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED -cp ~/aeron/aeron-all-*.jar -Daeron.dir=/dev/shm/$aerondir io.aeron.samples.AeronStat watch=false
