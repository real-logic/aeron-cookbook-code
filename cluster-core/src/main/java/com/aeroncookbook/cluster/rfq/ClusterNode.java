/*
 * Copyright 2019-2021 Shaun Laurens.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aeroncookbook.cluster.rfq;

import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ClusterNode
{
    private static final String LOCALHOST = "localhost";
    private static final int PORT_BASE = 9000;
    private static final int PORTS_PER_NODE = 100;
    private static final int ARCHIVE_CONTROL_REQUEST_PORT_OFFSET = 1;
    private static final int ARCHIVE_CONTROL_RESPONSE_PORT_OFFSET = 2;
    private static final int CLIENT_FACING_PORT_OFFSET = 3;
    private static final int MEMBER_FACING_PORT_OFFSET = 4;
    private static final int LOG_PORT_OFFSET = 5;
    private static final int TRANSFER_PORT_OFFSET = 6;
    private static final int LOG_CONTROL_PORT_OFFSET = 7;
    private final ShutdownSignalBarrier barrier;
    private final Logger log = LoggerFactory.getLogger(ClusterNode.class);
    private MediaDriver.Context mediaDriverContext;
    private ConsensusModule.Context consensusModuleContext;
    private Archive.Context archiveContext;
    private AeronArchive.Context aeronArchiveContext;
    private ClusteredServiceContainer.Context serviceContainerContext;
    private RfqClusteredService service;
    private ClusteredMediaDriver clusteredMediaDriver;
    private ClusteredServiceContainer container;

    public ClusterNode(ShutdownSignalBarrier barrier)
    {
        this.barrier = barrier;
    }

    /* As seen in BasicAuctionClusteredServiceNode in Aeron Samples */
    static int calculatePort(final int nodeId, final int offset)
    {
        return PORT_BASE + (nodeId * PORTS_PER_NODE) + offset;
    }

    private static String logControlChannel(final int nodeId, final String hostname, final int portOffset)
    {
        final int port = calculatePort(nodeId, portOffset);
        return new ChannelUriStringBuilder()
            .media("udp")
            .termLength(64 * 1024)
            .controlMode("manual")
            .controlEndpoint(hostname + ":" + port)
            .build();
    }

    public static String clusterMembers(final List<String> hostnames)
    {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hostnames.size(); i++)
        {
            sb.append(i);
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, CLIENT_FACING_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, MEMBER_FACING_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, LOG_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, TRANSFER_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':')
                .append(calculatePort(i, ARCHIVE_CONTROL_REQUEST_PORT_OFFSET));
            sb.append('|');
        }

        return sb.toString();
    }

    private String udpChannel(final int nodeId, final String hostname, final int portOffset)
    {
        final int port = calculatePort(nodeId, portOffset);
        return new ChannelUriStringBuilder()
            .media("udp")
            .termLength(64 * 1024)
            .endpoint(hostname + ":" + port)
            .build();
    }

    public void start(final boolean deleteOnStart)
    {
        final String aeronDirName = CommonContext.getAeronDirectoryName() + "2";
        final File baseDir = new File(System.getProperty("user.dir"), "aeron-cluster");
        log.info("Aeron Dir = {}", aeronDirName);
        log.info("Cluster Dir = {}", baseDir.getAbsolutePath());
        mediaDriverContext = new MediaDriver.Context();
        consensusModuleContext = new ConsensusModule.Context();
        archiveContext = new Archive.Context();
        aeronArchiveContext = new AeronArchive.Context();
        serviceContainerContext = new ClusteredServiceContainer.Context();

        service = new RfqClusteredService();

        mediaDriverContext
            .threadingMode(ThreadingMode.SHARED)
            .aeronDirectoryName(aeronDirName)
            .errorHandler(Throwable::printStackTrace)
            .terminationHook(barrier::signal)
            .dirDeleteOnShutdown(true)
            .dirDeleteOnStart(true);

        archiveContext
            .recordingEventsEnabled(false)
            .controlChannel("aeron:ipc")
            .aeronDirectoryName(aeronDirName)
            .threadingMode(ArchiveThreadingMode.SHARED);

        aeronArchiveContext
            .controlRequestChannel(archiveContext.controlChannel())
            .controlRequestStreamId(archiveContext.controlStreamId())
            .controlResponseChannel("aeron:ipc")
            .aeronDirectoryName(aeronDirName);

        consensusModuleContext
            .errorHandler(Throwable::printStackTrace)
            .clusterMemberId(0)
                .maxConcurrentSessions(50)
            .clusterMembers(clusterMembers(Arrays.asList(LOCALHOST)))
            .aeronDirectoryName(aeronDirName)
            .clusterDir(new File(baseDir, "consensus-module"))
            .ingressChannel("aeron:udp?term-length=64k")
            .logChannel(logControlChannel(0, LOCALHOST, LOG_CONTROL_PORT_OFFSET))
            .archiveContext(aeronArchiveContext.clone())
            .deleteDirOnStart(deleteOnStart);

        serviceContainerContext
            .aeronDirectoryName(aeronDirName)
            .archiveContext(aeronArchiveContext.clone())
            .clusterDir(new File(baseDir, "service"))
            .clusteredService(service)
            .errorHandler(Throwable::printStackTrace);

        clusteredMediaDriver = ClusteredMediaDriver.launch(
            mediaDriverContext,
            archiveContext,
            consensusModuleContext
        );

        container = ClusteredServiceContainer.launch(serviceContainerContext);
    }
}
