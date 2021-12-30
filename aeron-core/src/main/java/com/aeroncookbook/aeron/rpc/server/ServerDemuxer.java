/*
 * Copyright 2019-2022 Shaun Laurens.
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

import com.aeroncookbook.sbe.MessageHeaderDecoder;
import com.aeroncookbook.sbe.MessageHeaderEncoder;
import com.aeroncookbook.sbe.RpcConnectRequestDecoder;
import com.aeroncookbook.sbe.RpcRequestMethodDecoder;
import com.aeroncookbook.sbe.RpcResponseEventEncoder;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.agrona.CloseHelper.quietClose;

public class ServerDemuxer implements FragmentHandler
{
    private final Aeron aeron;
    private final Logger log = LoggerFactory.getLogger(ServerDemuxer.class);
    private final ShutdownSignalBarrier barrier;
    private final RpcConnectRequestDecoder connectRequest;
    private final RpcRequestMethodDecoder requestMethod;
    private final MessageHeaderEncoder headerEncoder;
    private final MessageHeaderDecoder headerDecoder;
    private final RpcResponseEventEncoder responseEvent;
    private final ExpandableDirectByteBuffer buffer;
    private Publication publication;

    public ServerDemuxer(Aeron aeron, ShutdownSignalBarrier barrier)
    {
        this.connectRequest = new RpcConnectRequestDecoder();
        this.requestMethod = new RpcRequestMethodDecoder();
        this.responseEvent = new RpcResponseEventEncoder();
        this.headerDecoder = new MessageHeaderDecoder();
        this.headerEncoder = new MessageHeaderEncoder();
        this.buffer = new ExpandableDirectByteBuffer(512);
        this.aeron = aeron;
        this.barrier = barrier;
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        headerDecoder.wrap(buffer, offset);
        final int headerLength = headerDecoder.encodedLength();
        final int actingLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        switch (headerDecoder.templateId())
        {
            case RpcConnectRequestDecoder.TEMPLATE_ID:
                connectRequest.wrap(buffer, offset + headerLength,
                        actingLength, actingVersion);
                final int streamId = connectRequest.returnConnectStream();
                final String uri = connectRequest.returnConnectUri();
                blockingOpenConnection(streamId, uri);
                break;
            case RpcRequestMethodDecoder.TEMPLATE_ID:
                requestMethod.wrap(buffer, offset + headerLength,
                        actingLength, actingVersion);
                final String parameters = requestMethod.parameters();
                final String correlation = requestMethod.correlation();
                respond(parameters, correlation);
                break;
            default:
                break;
        }
    }

    private void respond(String parameters, String correlation)
    {
        final String returnValue = parameters.toUpperCase();

        log.info("responding on correlation {} with value {}", correlation, returnValue);

        responseEvent.wrapAndApplyHeader(buffer, 0, headerEncoder);
        responseEvent.result(returnValue);
        responseEvent.correlation(correlation);

        int retries = 3;
        do
        {
            long result = publication.offer(buffer, 0, headerEncoder.encodedLength() + responseEvent.encodedLength());
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

    private void blockingOpenConnection(int streamId, String uri)
    {
        log.info("Received connect request with response URI {} stream {}", uri, streamId);
        publication = aeron.addExclusivePublication(uri, streamId);
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
