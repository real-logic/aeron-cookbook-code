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

package com.aeroncookbook.cluster.rfq.client;

import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RfqAcceptedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqCreatedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqQuotedEvent;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.Header;
import io.eider.util.EiderHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.SystemEpochClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//this client represents a gateway supporting multiple
// end user client
public class RfqClusterClient implements EgressListener
{
    private final Logger log = LoggerFactory.getLogger(RfqClusterClient.class);
    private final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy();
    private AeronCluster clusterClient;
    private boolean rfqCreated = false;
    private boolean rfqQuoteAdded = false;
    private boolean completed = false;
    private int rfqId;
    private int rfqQuoteId;
    private EpochClock epochClock = new SystemEpochClock();


    private DirectBuffer buffer = new ExpandableDirectByteBuffer(100);

    private AddInstrumentCommand addInstrumentCommand;
    private QuoteRfqCommand quoteRfqCommand;
    private CreateRfqCommand createRfqCommand;
    private AcceptRfqCommand acceptRfqCommand;


    public void start()
    {
        log.info("Starting");
        buildCommands();
        injectInstruments();
        log.info("User 1 Creating RFQ");
        client1CreateRfq();
        do
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
        while (!rfqCreated);
        log.info("User 2 Quoting RFQ {}", rfqId);
        client2QuoteRfq();
        do
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
        while (!rfqQuoteAdded);
        log.info("User 1 Accepting Quote {} on RFQ {}", rfqQuoteId, rfqId);
        client1AcceptQuote();
        do
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
        while (!completed);
        log.info("Flow complete");
        clusterClient.close();
        System.exit(0);
    }

    private void client1AcceptQuote()
    {
        acceptRfqCommand.writeHeader();
        acceptRfqCommand.writeRfqQuoteId(rfqQuoteId);
        acceptRfqCommand.writeRfqId(rfqId);
        acceptRfqCommand.writeUserId(1);

        offer(buffer, 0, AcceptRfqCommand.BUFFER_LENGTH);
    }

    private void client2QuoteRfq()
    {
        quoteRfqCommand.writeHeader();
        quoteRfqCommand.writeRfqId(rfqId);
        quoteRfqCommand.writeResponderId(2);
        quoteRfqCommand.writePrice(250);

        offer(buffer, 0, QuoteRfqCommand.BUFFER_LENGTH);
    }

    private void client1CreateRfq()
    {
        createRfqCommand.writeHeader();
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeQuantity(250);
        createRfqCommand.writeSide((short)0);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeExpireTimeMs(epochClock.time() + 60000);
        createRfqCommand.writeUserId(1);

        offer(buffer, 0, CreateRfqCommand.BUFFER_LENGTH);
    }

    private void injectInstruments()
    {
        addInstrumentCommand.writeHeader();
        addInstrumentCommand.writeEnabled(true);
        addInstrumentCommand.writeCusip("CUSIP");
        addInstrumentCommand.writeMinSize(100);
        addInstrumentCommand.writeSecurityId(688);
        offer(buffer, 0, AddInstrumentCommand.BUFFER_LENGTH);
    }

    private void buildCommands()
    {
        addInstrumentCommand = new AddInstrumentCommand();
        addInstrumentCommand.setUnderlyingBuffer(buffer, 0);

        createRfqCommand = new CreateRfqCommand();
        createRfqCommand.setUnderlyingBuffer(buffer, 0);

        quoteRfqCommand = new QuoteRfqCommand();
        quoteRfqCommand.setUnderlyingBuffer(buffer, 0);

        acceptRfqCommand = new AcceptRfqCommand();
        acceptRfqCommand.setUnderlyingBuffer(buffer, 0);
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
        final short eiderId = EiderHelper.getEiderId(buffer, offset);
        switch (eiderId)
        {
            case RfqCreatedEvent.EIDER_ID:
                final RfqCreatedEvent event = new RfqCreatedEvent();
                event.setUnderlyingBuffer(buffer, offset);
                rfqId = event.readRfqId();
                log.info("rfq created with ID {}", rfqId);
                rfqCreated = true;
                break;
            case RfqErrorEvent.EIDER_ID:
                final RfqErrorEvent eventError = new RfqErrorEvent();
                eventError.setUnderlyingBuffer(buffer, offset);
                log.info("error: {}", eventError.readError());
                break;
            case RfqQuotedEvent.EIDER_ID:
                final RfqQuotedEvent quotedEvent = new RfqQuotedEvent();
                quotedEvent.setUnderlyingBuffer(buffer, offset);
                rfqQuoteId = quotedEvent.readRfqQuoteId();
                rfqQuoteAdded = true;
                log.info("Quote added, response id {}", quotedEvent.readRfqQuoteId());
                break;
            case RfqAcceptedEvent.EIDER_ID:
                final RfqAcceptedEvent acceptedEvent = new RfqAcceptedEvent();
                acceptedEvent.setUnderlyingBuffer(buffer, offset);
                log.info("Rfq accepted");
                completed = true;
                break;
            default:
                break;
        }

    }

    public void setAeronCluster(final AeronCluster clusterClient)
    {
        this.clusterClient = clusterClient;
    }

    private void offer(final DirectBuffer buffer, final int offset, final int length)
    {
        while (clusterClient.offer(buffer, offset, length) < 0)
        {
            idleStrategy.idle(clusterClient.pollEgress());
        }
    }

}
