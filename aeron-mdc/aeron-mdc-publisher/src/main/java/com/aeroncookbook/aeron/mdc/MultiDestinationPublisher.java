package com.aeroncookbook.aeron.mdc;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiDestinationPublisher
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MultiDestinationPublisher.class);

    public static void main(final String[] args)
    {
        final var mdcHost = System.getenv().get("MDCHOST");
        final var controlPort = System.getenv().get("CONTROLPORT");

        if (mdcHost == null || controlPort == null)
        {
            LOGGER.error("requires 2 env vars: MDCHOST, CONTROLPORT");
        }
        else
        {
            final var controlChannelPort = Integer.parseInt(controlPort);
            final var barrier = new ShutdownSignalBarrier();
            final var hostAgent = new MultiDestinationPublisherAgent(mdcHost, controlChannelPort);
            final var runner =
                new AgentRunner(new SleepingMillisIdleStrategy(), MultiDestinationPublisher::errorHandler,
                null, hostAgent);

            AgentRunner.startOnThread(runner);

            barrier.await();

            CloseHelper.quietClose(runner);
        }
    }

    private static void errorHandler(final Throwable throwable)
    {
        LOGGER.error("agent failure {}", throwable.getMessage(), throwable);
    }
}
