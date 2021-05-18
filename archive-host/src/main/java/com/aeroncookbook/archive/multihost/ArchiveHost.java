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
        if (args.length != 3)
        {
            LOGGER.error("requires 3 parameters: this host IP, control channel port and recording events channel port");
        } else
        {
            final var hostIp = args[0];
            final var controlChannelPort = Integer.parseInt(args[1]);
            final var recEventsChannelPort = Integer.parseInt(args[2]);
            final var barrier = new ShutdownSignalBarrier();
            final var hostAgent = new ArchiveHostAgent(hostIp, controlChannelPort, recEventsChannelPort);
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
