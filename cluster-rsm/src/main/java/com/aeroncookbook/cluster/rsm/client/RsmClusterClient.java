/*
 * Copyright 2019-2023 Adaptive Financial Consulting Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aeroncookbook.cluster.rsm.client;

import com.aeroncookbook.cluster.async.sbe.AddCommandEncoder;
import com.aeroncookbook.cluster.async.sbe.CurrentValueEventDecoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderEncoder;
import com.aeroncookbook.cluster.async.sbe.MultiplyCommandEncoder;
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
    private final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(128);
    private final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy();
    private final AddCommandEncoder addCommand = new AddCommandEncoder();
    private final MultiplyCommandEncoder multiplyCommand = new MultiplyCommandEncoder();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final CurrentValueEventDecoder currentValue = new CurrentValueEventDecoder();
    private AeronCluster clusterClient;
    private boolean allResultsReceived;
    private int correlation = 0;
    private static final int MESSAGES_TO_SEND = 100;

    public void start()
    {
        log.info("Starting");
        boolean done = false;
        allResultsReceived = false;
        messageHeaderDecoder.wrap(buffer, 0);
        while (!Thread.currentThread().isInterrupted() && !done)
        {
            if (correlation % 33 == 0)
            {
                multiplyCommand.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
                multiplyCommand.correlation(correlation);
                multiplyCommand.value(2);
                log.info("Multiplying by {}; correlation = {}", 2, correlation);
                offer(buffer, MessageHeaderEncoder.ENCODED_LENGTH + multiplyCommand.encodedLength());
            }
            else
            {
                addCommand.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
                addCommand.correlation(correlation);
                addCommand.value(correlation); //incrementing value, also used for correlation
                log.info("Adding {}; correlation = {}", correlation, correlation);
                offer(buffer, MessageHeaderEncoder.ENCODED_LENGTH + addCommand.encodedLength());
            }
            correlation++;

            if (correlation > MESSAGES_TO_SEND)
            {
                done = true;
            }

            idleStrategy.idle(clusterClient.pollEgress());
        }
        while (!allResultsReceived)
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
        log.info("Done (async results may still arrive); keeping alive. Ctrl-C to kill");
        while (!Thread.currentThread().isInterrupted())
        {
            clusterClient.sendKeepAlive();
            idleStrategy.idle(clusterClient.pollEgress());
        }
    }

    @Override
    public void onMessage(
        final long clusterSessionId,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        final int templateId = messageHeaderDecoder.templateId();
        if (templateId == CurrentValueEventDecoder.TEMPLATE_ID)
        {
            currentValue.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
            final long correlationId = currentValue.correlation();
            final long value = currentValue.value();

            if (correlation == MESSAGES_TO_SEND)
            {
                allResultsReceived = true;
            }
            log.info("Current value is {}; correlation = {}", value, correlationId);
        }
        else
        {
            log.warn("unknown message {}", templateId);
        }
    }

    public void setAeronCluster(final AeronCluster clusterClient)
    {
        this.clusterClient = clusterClient;
    }

    private void offer(final MutableDirectBuffer buffer, final int length)
    {
        while (clusterClient.offer(buffer, 0, length) < 0)
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
    }
}
