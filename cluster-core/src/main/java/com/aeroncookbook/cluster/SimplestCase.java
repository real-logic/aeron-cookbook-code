package com.aeroncookbook.cluster;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.Image;
import io.aeron.Publication;
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
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.console.ContinueBarrier;

public class SimplestCase
{
    static final ExpandableArrayBuffer msgBuffer = new ExpandableArrayBuffer();
    static final EgressListener egressMessageListener = new EgressListener()
    {
        @Override
        public void onMessage(long clusterSessionId, long timestamp, DirectBuffer buffer, int offset, int length,
                              Header header)
        {
            System.out.println("got message from " + clusterSessionId);
        }

        @Override
        public void newLeader(long clusterSessionId, long leadershipTermId, int leaderMemberId, String memberEndpoints)
        {
            System.out.println("new leader");
        }
    };
    static Aeron aeron;
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
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
            .errorHandler(Throwable::printStackTrace)
            .dirDeleteOnShutdown(true)
            .dirDeleteOnStart(true);

        archiveContext
            .recordingEventsEnabled(true)
            .threadingMode(ArchiveThreadingMode.SHARED);

        consensusModuleContext
            .errorHandler(Throwable::printStackTrace)
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

    static void sendMessage(final int id, int messageLength)
    {
        msgBuffer.putInt(0, id);
        while (client.offer(msgBuffer, 0, messageLength) < 0)
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
        start(false);
        connectClient();
        sendMessage(1, 4);
        pollEgress();
        ContinueBarrier barrier = new ContinueBarrier("continue");
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
                .egressListener(egressMessageListener)
                .aeronDirectoryName(aeronDirName)
        );

    }

    static class Service implements ClusteredService
    {

        private Cluster cluster;

        @Override
        public void onStart(Cluster cluster, Image snapshotImage)
        {
            this.cluster = cluster;
        }

        @Override
        public void onSessionOpen(ClientSession session, long timestamp)
        {
            System.out.println("session opened from session " + session.id());
        }

        @Override
        public void onSessionClose(ClientSession session, long timestamp, CloseReason closeReason)
        {
            System.out.println("session closed id=" + session.id());
        }

        @Override
        public void onSessionMessage(ClientSession session, long timestamp, DirectBuffer buffer, int offset,
                                     int length, Header header)
        {
            System.out.println("session message");
            while (session.offer(buffer, offset, length) < 0)
            {
                Thread.yield();
            }
        }

        @Override
        public void onTimerEvent(long correlationId, long timestamp)
        {
            System.out.println("session timer fired");

        }

        @Override
        public void onTakeSnapshot(Publication snapshotPublication)
        {
            System.out.println("session opened");

        }

        @Override
        public void onRoleChange(Cluster.Role newRole)
        {
            System.out.println("role change to " + newRole);
        }

        @Override
        public void onTerminate(Cluster cluster)
        {
            System.out.println("terminated");

        }

        protected long serviceCorrelationId(final int correlationId)
        {
            return ((long) cluster.context().serviceId()) << 32 | correlationId;
        }

    }

}
