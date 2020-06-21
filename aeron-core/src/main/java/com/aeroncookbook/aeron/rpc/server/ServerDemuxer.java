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

package com.aeroncookbook.aeron.rpc.server;

import com.aeroncookbook.aeron.rpc.gen.ConnectRequest;
import com.aeroncookbook.aeron.rpc.gen.RequestMethod;
import com.aeroncookbook.aeron.rpc.gen.ResponseEvent;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.util.EiderHelper.getEiderId;
import static org.agrona.CloseHelper.quietClose;

public class ServerDemuxer implements FragmentHandler
{
    private final Aeron aeron;
    private final Logger log = LoggerFactory.getLogger(ServerDemuxer.class);
    private final ShutdownSignalBarrier barrier;
    private final ConnectRequest connectRequest;
    private final RequestMethod requestMethod;
    private final ResponseEvent responseEvent;
    private final ExpandableDirectByteBuffer buffer;
    private Publication publication;

    public ServerDemuxer(Aeron aeron, ShutdownSignalBarrier barrier)
    {
        this.connectRequest = new ConnectRequest();
        this.requestMethod = new RequestMethod();
        this.responseEvent = new ResponseEvent();
        this.buffer = new ExpandableDirectByteBuffer(ResponseEvent.BUFFER_LENGTH);
        this.aeron = aeron;
        this.barrier = barrier;
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);
        switch (eiderId)
        {
            case ConnectRequest.EIDER_ID:
                connectRequest.setUnderlyingBuffer(buffer, offset);
                blockingOpenConnection(connectRequest);
                break;
            case RequestMethod.EIDER_ID:
                requestMethod.setUnderlyingBuffer(buffer, offset);
                respond(requestMethod);
                break;
            default:
                break;
        }
    }

    private void respond(RequestMethod requestMethod)
    {
        int retries = 3;
        do
        {
            responseEvent.setBufferWriteHeader(buffer, 0);
            responseEvent.writeCorrelation(requestMethod.readCorrelation());

            final String returnValue = requestMethod.readParameter().toUpperCase();

            log.info("responding on correlation {} with value {}", requestMethod.readCorrelation(), returnValue);

            responseEvent.writeResultWithPadding(returnValue);
            long result = publication.offer(buffer, 0, ResponseEvent.BUFFER_LENGTH);
            if (result > 0)
            {
                //shutdown once the result is sent
                barrier.signal();
                break;
            } else
            {
                log.warn("aeron returned {}", result);
            }
        }
        while (--retries > 0);
    }

    private void blockingOpenConnection(ConnectRequest connectRequest)
    {
        log.info("received connect request with response URI {} stream {}",
            connectRequest.readReturnConnectUri(),
            connectRequest.readReturnConnectStream());

        publication = aeron.addExclusivePublication(connectRequest.readReturnConnectUri(),
            connectRequest.readReturnConnectStream());

        while (!publication.isConnected())
        {
            aeron.context().idleStrategy().idle();
        }
    }

    public void closePublication()
    {
        quietClose(publication);
    }
}
