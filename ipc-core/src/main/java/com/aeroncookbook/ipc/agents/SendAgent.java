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

package com.aeroncookbook.ipc.agents;

import io.aeron.Publication;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class SendAgent implements Agent
{
    private final Publication publication;
    private final int sendCount;
    private final UnsafeBuffer unsafeBuffer;
    private int currentCountItem = 1;
    private final Logger logger = LoggerFactory.getLogger(SendAgent.class);

    public SendAgent(final Publication publication, int sendCount)
    {
        this.publication = publication;
        this.sendCount = sendCount;
        this.unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(64));
        unsafeBuffer.putInt(0, currentCountItem);
    }

    @Override
    public int doWork()
    {
        if (currentCountItem > sendCount)
        {
            return 0;
        }

        if (publication.isConnected())
        {
            if (publication.offer(unsafeBuffer) > 0)
            {
                logger.info("sent: {}", currentCountItem);
                currentCountItem += 1;
                unsafeBuffer.putInt(0, currentCountItem);
            }
        }
        return 0;
    }

    @Override
    public String roleName()
    {
        return "sender";
    }
}
