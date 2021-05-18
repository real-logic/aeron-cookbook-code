ARG REPO_NAME=docker.io/
ARG IMAGE_NAME=aeroncookbook/jdk16
ARG IMAGE_TAG=latest
FROM ${REPO_NAME}${IMAGE_NAME}:${IMAGE_TAG}

SHELL [ "/bin/bash", "-o", "pipefail", "-c" ]

COPY --chmod=755 ./build/libs/archive-host-0.1-SNAPSHOT-all.jar /usr/local/jars/archive-host-0.1-SNAPSHOT-all.jar

CMD ["java --illegal-access=permit -jar /usr/local/jars/archive-host-0.1-SNAPSHOT-all.jar 127.0.0.1 8000 8001"]
