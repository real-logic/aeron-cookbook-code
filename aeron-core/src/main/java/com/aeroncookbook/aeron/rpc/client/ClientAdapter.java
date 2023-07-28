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

package com.aeroncookbook.aeron.rpc.client;

import com.aeroncookbook.sbe.MessageHeaderDecoder;
import com.aeroncookbook.sbe.RpcResponseEventDecoder;
import com.aeroncookbook.sbe.RpcResponseEventEncoder;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClientAdapter implements FragmentHandler
{
    private final Logger logger = LoggerFactory.getLogger(ClientAdapter.class);
    private final RpcResponseEventDecoder responseEvent;
    private final MessageHeaderDecoder headerDecoder;
    private final ShutdownSignalBarrier barrier;

    public ClientAdapter(final ShutdownSignalBarrier barrier)
    {
        this.barrier = barrier;
        this.responseEvent = new RpcResponseEventDecoder();
        this.headerDecoder = new MessageHeaderDecoder();
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        headerDecoder.wrap(buffer, offset);

        if (headerDecoder.templateId() == RpcResponseEventEncoder.TEMPLATE_ID)
        {
            responseEvent.wrap(buffer, offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(), headerDecoder.version());
            logger.info("Received {}", responseEvent.result());
            barrier.signal();
        }
        else
        {
            logger.warn("Unknown message");
        }
    }
}
