ARG REPO_NAME=docker.io/
ARG IMAGE_NAME=aeroncookbook/jdk17
ARG IMAGE_TAG=latest
FROM ${REPO_NAME}${IMAGE_NAME}:${IMAGE_TAG}
SHELL [ "/bin/bash", "-o", "pipefail", "-c" ]
WORKDIR /root/jar/
COPY --chmod=755 /build/libs/aeron-mdc-publisher-uber.jar /root/jar/aeron-mdc-publisher-uber.jar
COPY --chmod=755 entrypoint.sh /root/jar/entrypoint.sh
ENTRYPOINT ["/root/jar/entrypoint.sh"]
