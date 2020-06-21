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

package com.aeroncookbook.aeron.rpc.client;

import com.aeroncookbook.aeron.rpc.gen.ResponseEvent;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.util.EiderHelper.getEiderId;

public class ClientDemuxer implements FragmentHandler
{
    private final Logger logger = LoggerFactory.getLogger(ClientDemuxer.class);
    private final ResponseEvent responseEvent;
    private final ShutdownSignalBarrier barrier;

    public ClientDemuxer(ShutdownSignalBarrier barrier)
    {
        this.barrier = barrier;
        responseEvent = new ResponseEvent();
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);
        switch (eiderId)
        {
            case ResponseEvent.EIDER_ID:
                responseEvent.setUnderlyingBuffer(buffer, offset);
                logger.info("Received {}", responseEvent.readResult());
                barrier.signal();
                break;
            default:
                logger.warn("Unknown message");
                break;
        }
    }
}
