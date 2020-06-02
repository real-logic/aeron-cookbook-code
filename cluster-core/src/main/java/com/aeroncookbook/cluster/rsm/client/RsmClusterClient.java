/*
 * Copyright 2019-2020 Shaun Laurens.
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

package com.aeroncookbook.cluster.rsm.client;

import com.aeroncookbook.cluster.rsm.protocol.AddCommand;
import com.aeroncookbook.cluster.rsm.protocol.CurrentValueEvent;
import com.aeroncookbook.cluster.rsm.protocol.EiderHelper;
import com.aeroncookbook.cluster.rsm.protocol.MultiplyCommand;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RsmClusterClient implements EgressListener
{
    private final Logger log = LoggerFactory.getLogger(RsmClusterClient.class);
    private final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(AddCommand.BUFFER_LENGTH);
    private final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy();
    private final AddCommand addCommand = new AddCommand();
    private final MultiplyCommand multiplyCommand = new MultiplyCommand();
    private final CurrentValueEvent event = new CurrentValueEvent();
    private AeronCluster clusterClient;
    private boolean allResultsReceived;
    private int injectedValue = 1;

    public void start()
    {
        log.info("Starting");
        addCommand.setUnderlyingBuffer(buffer, 0);
        multiplyCommand.setUnderlyingBuffer(buffer, 0);
        boolean done = false;
        allResultsReceived = false;
        while (!Thread.currentThread().isInterrupted() && !done)
        {
            if (injectedValue % 33 == 0)
            {
                multiplyCommand.writeHeader();
                multiplyCommand.writeCorrelation(injectedValue);
                multiplyCommand.writeValue(2);
                log.info("Multiplying by {}; correlation = {}", 2, injectedValue);
                offer(buffer, 0, MultiplyCommand.BUFFER_LENGTH);
            }
            else
            {
                addCommand.writeHeader();
                addCommand.writeCorrelation(injectedValue);
                addCommand.writeValue(injectedValue);
                log.info("Adding {}; correlation = {}", injectedValue, injectedValue);
                offer(buffer, 0, AddCommand.BUFFER_LENGTH);
            }
            injectedValue++;

            if (injectedValue > 100)
            {
                done = true;
            }

            idleStrategy.idle(clusterClient.pollEgress());
        }
        injectedValue--;
        while (!allResultsReceived)
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
        log.info("Done; keeping alive. Ctrl-C to kill");
        while (!Thread.currentThread().isInterrupted())
        {
            clusterClient.sendKeepAlive();
            idleStrategy.idle();
        }
    }

    @Override
    public void onMessage(long clusterSessionId, long timestamp, DirectBuffer buffer,
                          int offset, int length, Header header)
    {
        short eiderId = EiderHelper.getEiderId(buffer, offset);
        if (eiderId == CurrentValueEvent.EIDER_ID)
        {
            event.setUnderlyingBuffer(buffer, offset);
            if (event.readCorrelation() == injectedValue)
            {
                allResultsReceived = true;
            }
            log.info("Current value is {}; correlation = {}", event.readValue(), event.readCorrelation());
        }
        else
        {
            log.warn("unknown message {}", eiderId);
        }
    }

    public void setAeronCluster(AeronCluster clusterClient)
    {
        this.clusterClient = clusterClient;
    }

    private void offer(MutableDirectBuffer buffer, int offset, int length)
    {
        while (clusterClient.offer(buffer, offset, length) < 0)
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
    }

}
