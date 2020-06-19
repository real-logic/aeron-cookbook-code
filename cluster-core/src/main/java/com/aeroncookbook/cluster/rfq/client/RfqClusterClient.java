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

package com.aeroncookbook.cluster.rfq.client;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RfqClusterClient implements EgressListener
{
    private final Logger log = LoggerFactory.getLogger(RfqClusterClient.class);
    private final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy();
    private AeronCluster clusterClient;

    public void start()
    {
        log.info("Starting");
    }

    @Override
    public void onMessage(long clusterSessionId, long timestamp, DirectBuffer buffer,
                          int offset, int length, Header header)
    {
        log.info("Got message");

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
