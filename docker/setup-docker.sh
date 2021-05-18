#!/bin/bash

echo "debconf debconf/frontend select noninteractive" | debconf-set-selections

apt-get update --quiet

apt-get dist-upgrade --quiet --assume-yes

apt-get install \
    --quiet \
    --assume-yes \
     --no-install-recommends \
    bash \
    wget \
    iproute2 \
    less \
    procps \
    sysstat

mkdir /usr/local/aeron

wget https://repo1.maven.org/maven2/io/aeron/aeron-all/1.33.1/aeron-all-1.33.1.jar -P /usr/local/aeron/

