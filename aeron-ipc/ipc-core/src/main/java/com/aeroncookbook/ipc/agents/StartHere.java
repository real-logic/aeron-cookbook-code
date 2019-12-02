package com.aeroncookbook.ipc.agents;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingIdleStrategy;

public class StartHere
{
    public static void main(String[] args)
    {
        final String channel = "aeron:ipc";
        final int stream = 10;
        final int sendCount = 1000;
        final IdleStrategy idleStrategy = new SleepingIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        //construct Media Driver, cleaning up media driver folder on start/stop
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true);
        final MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverCtx);

        //construct Aeron, pointing at the media driver's folder
        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName());
        final Aeron aeron = Aeron.connect(aeronCtx);

        //construct the subs and pubs
        final Subscription subscription = aeron.addSubscription(channel, stream);
        final Publication publication = aeron.addPublication(channel, stream);

        //construct the agents
        final SendAgent sendAgent = new SendAgent(publication, sendCount);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount);

        //construct agent runners
        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategy,
                Throwable::printStackTrace, null, sendAgent);
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategy,
                Throwable::printStackTrace, null, receiveAgent);

        //start the runners
        AgentRunner.startOnThread(sendAgentRunner);
        AgentRunner.startOnThread(receiveAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();

        //close the resources
        receiveAgentRunner.close();
        sendAgentRunner.close();
        aeron.close();
        mediaDriver.close();
    }
}
