/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.cluster;

import io.aeron.CommonContext;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.console.ContinueBarrier;

import java.util.concurrent.TimeUnit;

public class SimplestCase
{
    static final ExpandableArrayBuffer MSG_BUFFER = new ExpandableArrayBuffer();
    static final EgressListener EGRESS_LISTENER = new EgressListener()
    {
        @Override
        public void onMessage(
            final long clusterSessionId,
            final long timestamp,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Header header)
        {
            System.out.println("got message from " + clusterSessionId);
        }

        @Override
        public void onNewLeader(final long clusterSessionId, final long leadershipTermId,
            final int leaderMemberId, final String ingressEndpoints)
        {
            System.out.println("new leader");
        }
    };
    static ClusteredMediaDriver clusteredMediaDriver;
    static ClusteredServiceContainer container;
    static SimplestCase.Service service;

    static MediaDriver clientMediaDriver;
    static AeronCluster client;

    static void start(final boolean deleteOnStart)
    {
        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context();
        final ConsensusModule.Context consensusModuleContext = new ConsensusModule.Context();
        final Archive.Context archiveContext = new Archive.Context();
        final ClusteredServiceContainer.Context serviceContainerContext = new ClusteredServiceContainer.Context();

        service = new SimplestCase.Service();

        mediaDriverContext
            .threadingMode(ThreadingMode.SHARED)
            .errorHandler(Throwable::printStackTrace)
            .dirDeleteOnShutdown(true)
            .dirDeleteOnStart(true);

        archiveContext
            .recordingEventsEnabled(true)
            .threadingMode(ArchiveThreadingMode.SHARED);

        consensusModuleContext
            .errorHandler(Throwable::printStackTrace)
            .replicationChannel("aeron:udp?endpoint=localhost:0")
            .deleteDirOnStart(deleteOnStart);

        serviceContainerContext
            .clusteredService(service)
            .errorHandler(Throwable::printStackTrace);

        clusteredMediaDriver = ClusteredMediaDriver.launch(
            mediaDriverContext,
            archiveContext,
            consensusModuleContext
        );

        container = ClusteredServiceContainer.launch(serviceContainerContext);
    }

    static void stop()
    {
        CloseHelper.quietClose(client);
        CloseHelper.quietClose(container);
        CloseHelper.quietClose(clusteredMediaDriver);
        CloseHelper.quietClose(clientMediaDriver);
    }

    static void sendMessage(final int id, final int messageLength)
    {
        MSG_BUFFER.putInt(0, id);
        while (client.offer(MSG_BUFFER, 0, messageLength) < 0)
        {
            client.pollEgress();
            Thread.yield();
        }
    }

    static void pollEgress()
    {
        while (client.pollEgress() <= 0)
        {
            Thread.yield();
        }
    }

    public static void main(final String[] args)
    {
        start(true); //set to false to see log replay in action
        connectClient();
        sendMessage(1, 4);
        pollEgress();
        final ContinueBarrier barrier = new ContinueBarrier("continue");
        barrier.await();
        stop();
    }

    static void connectClient()
    {
        final String aeronDirName = CommonContext.getAeronDirectoryName() + "-client";

        clientMediaDriver = MediaDriver.launch(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnStart(true)
                .errorHandler(Throwable::printStackTrace)
                .aeronDirectoryName(aeronDirName)
        );

        client = AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(Throwable::printStackTrace)
                .egressListener(EGRESS_LISTENER)
                .aeronDirectoryName(aeronDirName)
        );
    }

    static class Service implements ClusteredService
    {
        private Cluster cluster;

        @Override
        public void onStart(final Cluster cluster, final Image snapshotImage)
        {
            this.cluster = cluster;
        }

        @Override
        public void onSessionOpen(final ClientSession session, final long timestamp)
        {
            System.out.println("session opened from session= " + session.id());
        }

        @Override
        public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason)
        {
            System.out.println("session closed id=" + session.id());
        }

        @Override
        public void onSessionMessage(
            final ClientSession session,
            final long timestamp,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Header header)
        {
            System.out.println("session message");
            while (session.offer(buffer, offset, length) < 0)
            {
                Thread.yield();
            }
        }

        @Override
        public void onTimerEvent(final long correlationId, final long timestamp)
        {
            System.out.println("session timer fired");
        }

        @Override
        public void onTakeSnapshot(final ExclusivePublication snapshotPublication)
        {
            System.out.println("on take snapshot");
        }

        @Override
        public void onRoleChange(final Cluster.Role newRole)
        {
            System.out.println("role change to " + newRole);
        }

        @Override
        public void onTerminate(final Cluster cluster)
        {
            System.out.println("terminated");
        }

        @Override
        public void onNewLeadershipTermEvent(
            final long leadershipTermId,
            final long logPosition,
            final long timestamp,
            final long termBaseLogPosition,
            final int leaderMemberId,
            final int logSessionId,
            final TimeUnit timeUnit,
            final int appVersion)
        {
            System.out.println("new leadership term");
        }
    }

}
