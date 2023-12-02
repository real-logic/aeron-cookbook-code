ARG REPO_NAME=docker.io/
ARG IMAGE_NAME=azul/zulu-openjdk-debian
ARG IMAGE_TAG=21
FROM ${REPO_NAME}${IMAGE_NAME}:${IMAGE_TAG}

SHELL [ "/bin/bash", "-o", "pipefail", "-c" ]

COPY --chmod=755 setup-docker.sh /root/dockerbuild/setup-docker.sh
COPY --chmod=755 setup-docker.sh /root/aeron/
RUN /root/dockerbuild/setup-docker.sh && rm --recursive --force "/root/dockerbuild"

WORKDIR /root/jar/
COPY --chmod=755 /build/libs/cluster-uber.jar /root/jar/cluster-uber.jar
COPY --chmod=755 entrypoint.sh /root/jar/entrypoint.sh
COPY --chmod=755 describe.sh /root/jar/describe.sh
COPY --chmod=755 snapshot.sh /root/jar/snapshot.sh
COPY --chmod=755 aeronstat_single.sh /root/jar/aeronstat_single.sh
COPY --chmod=755 errorstat.sh /root/jar/errorstat.sh
COPY --chmod=755 lossstat.sh /root/jar/lossstat.sh
COPY --chmod=755 streamstat.sh /root/jar/streamstat.sh
COPY --chmod=755 stackdump.sh /root/jar/stackdump.sh
COPY --chmod=755 clustererrors.sh /root/jar/clustererrors.sh
COPY --chmod=755 noderole.sh /root/jar/noderole.sh
ENTRYPOINT ["/root/jar/entrypoint.sh"]