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

import com.aeroncookbook.aeron.rpc.Constants;
import com.aeroncookbook.aeron.rpc.gen.ConnectRequest;
import com.aeroncookbook.aeron.rpc.gen.RequestMethod;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.UUID.randomUUID;
import static org.agrona.CloseHelper.quietClose;

public class ClientAgent implements Agent
{
    private final Logger log = LoggerFactory.getLogger(ClientAgent.class);
    private final ExpandableDirectByteBuffer buffer;
    private final Aeron aeron;
    private final ClientDemuxer demuxer;
    private final ConnectRequest connectRequest;
    private final RequestMethod requestMethod;

    private State state;
    private ExclusivePublication publication;
    private Subscription subscription;

    public ClientAgent(Aeron aeron, ShutdownSignalBarrier barrier)
    {
        this.demuxer = new ClientDemuxer(barrier);
        this.aeron = aeron;
        this.buffer = new ExpandableDirectByteBuffer(98);
        this.connectRequest = new ConnectRequest();
        this.requestMethod = new RequestMethod();
    }

    @Override
    public void onStart()
    {
        log.info("Client starting");
        state = State.AWAITING_OUTBOUND_CONNECT;
        publication = aeron.addExclusivePublication(Constants.SERVER_URI, Constants.RPC_STREAM);
        subscription = aeron.addSubscription(Constants.CLIENT_URI, Constants.RPC_STREAM);
    }

    @Override
    public int doWork()
    {
        switch (state)
        {
            case AWAITING_OUTBOUND_CONNECT:
                awaitConnected();
                state = State.CONNECTED;
                break;
            case CONNECTED:
                sendConnectRequest();
                state = State.AWAITING_INBOUND_CONNECT;
                break;
            case AWAITING_INBOUND_CONNECT:
                awaitSubscriptionConnected();
                state = State.READY;
                break;
            case READY:
                sendMessage();
                state = State.AWAITING_RESULT;
                break;
            case AWAITING_RESULT:
                subscription.poll(demuxer, 1);
                break;
            default:
                break;
        }
        return 0;
    }

    private void sendMessage()
    {
        final String input = "string to be made uppercase";
        final String correlation = randomUUID().toString();

        requestMethod.setBufferWriteHeader(buffer, 0);
        requestMethod.writeCorrelation(correlation);
        requestMethod.writeParameterWithPadding(input);

        log.info("sending: {} with correlation {}", input, correlation);

        send(buffer, RequestMethod.BUFFER_LENGTH);
    }

    private void sendConnectRequest()
    {
        log.info("sending connect request");

        connectRequest.setBufferWriteHeader(buffer, 0);
        connectRequest.writeReturnConnectStream(Constants.RPC_STREAM);
        connectRequest.writeReturnConnectUriWithPadding(Constants.CLIENT_URI);
        connectRequest.writeSession(randomUUID().toString());

        send(buffer, ConnectRequest.BUFFER_LENGTH);
    }

    private void awaitSubscriptionConnected()
    {
        log.info("awaiting inbound server connect");

        while (!subscription.isConnected())
        {
            aeron.context().idleStrategy().idle();
        }
    }

    private void awaitConnected()
    {
        log.info("awaiting outbound server connect");

        while (!publication.isConnected())
        {
            aeron.context().idleStrategy().idle();
        }
    }

    @Override
    public void onClose()
    {
        quietClose(publication);
        quietClose(subscription);
    }

    @Override
    public String roleName()
    {
        return "client";
    }

    private void send(DirectBuffer buffer, int length)
    {
        int retries = 3;

        do
        {
            //in this example, the offset it always zero. This will not always be the case.
            long result = publication.offer(buffer, 0, length);
            if (result > 0)
            {
                break;
            } else
            {
                log.info("aeron returned {} on offer", result);
            }
        }
        while (--retries > 0);
    }

    enum State
    {
        AWAITING_OUTBOUND_CONNECT,
        CONNECTED,
        READY,
        AWAITING_RESULT,
        AWAITING_INBOUND_CONNECT
    }
}
