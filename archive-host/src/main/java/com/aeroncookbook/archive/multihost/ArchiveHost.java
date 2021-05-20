package com.aeroncookbook.archive.multihost;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveHost
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveHost.class);

    public static void main(String[] args)
    {
        final var archiveHost = System.getenv().get("ARCHIVEHOST");
        final var controlPort = System.getenv().get("CONTROLPORT");
        final var eventsPort = System.getenv().get("EVENTSPORT");

        if (archiveHost == null || controlPort == null || eventsPort == null)
        {
            LOGGER.error("requires 3 env vars: ARCHIVEHOST, CONTROLPORT, EVENTSPORT");
        } else
        {
            final var controlChannelPort = Integer.parseInt(controlPort);
            final var recEventsChannelPort = Integer.parseInt(eventsPort);
            final var barrier = new ShutdownSignalBarrier();
            final var hostAgent = new ArchiveHostAgent(archiveHost, controlChannelPort, recEventsChannelPort);
            final var runner =
                new AgentRunner(new SleepingMillisIdleStrategy(), ArchiveHost::errorHandler, null, hostAgent);

            AgentRunner.startOnThread(runner);

            barrier.await();

            CloseHelper.quietClose(runner);
        }
    }

    private static void errorHandler(Throwable throwable)
    {
        LOGGER.error("agent failure {}", throwable.getMessage(), throwable);
    }
}
