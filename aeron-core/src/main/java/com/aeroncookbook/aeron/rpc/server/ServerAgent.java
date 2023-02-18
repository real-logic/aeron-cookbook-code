/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.aeron.rpc.server;

import com.aeroncookbook.aeron.rpc.Constants;
import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerAgent implements Agent
{
    private final Aeron aeron;
    private final Logger log = LoggerFactory.getLogger(ServerAgent.class);
    private final ServerDemuxer demuxer;
    private Subscription subscription;

    public ServerAgent(final Aeron aeron, final ShutdownSignalBarrier barrier)
    {
        this.aeron = aeron;
        this.demuxer = new ServerDemuxer(aeron, barrier);
    }

    @Override
    public void onStart()
    {
        log.info("Server starting");
        subscription = aeron.addSubscription(Constants.SERVER_URI, Constants.RPC_STREAM);
    }

    @Override
    public int doWork() throws Exception
    {
        return subscription.poll(demuxer, 1);
    }

    @Override
    public void onClose()
    {
        demuxer.closePublication();
    }

    @Override
    public String roleName()
    {
        return "server";
    }
}
