/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.ipc.agents;

import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReceiveAgent implements Agent
{
    private final Subscription subscription;
    private final ShutdownSignalBarrier barrier;
    private final int sendCount;
    private final Logger logger = LoggerFactory.getLogger(ReceiveAgent.class);

    public ReceiveAgent(final Subscription subscription, final ShutdownSignalBarrier barrier, final int sendCount)
    {
        this.subscription = subscription;
        this.barrier = barrier;
        this.sendCount = sendCount;
    }

    @Override
    public int doWork() throws Exception
    {
        subscription.poll(this::handler, 100);
        return 0;
    }

    private void handler(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        final int lastValue = buffer.getInt(offset);

        if (lastValue >= sendCount)
        {
            logger.info("received: {}", lastValue);
            barrier.signal();
        }
    }

    @Override
    public String roleName()
    {
        return "receiver";
    }
}
