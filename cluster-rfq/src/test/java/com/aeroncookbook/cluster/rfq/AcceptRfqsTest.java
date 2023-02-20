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

package com.aeroncookbook.cluster.rfq;

import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CounterRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RfqAcceptedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqCreatedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqQuotedEvent;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import io.eider.util.EiderHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcceptRfqsTest
{
    private static final String CLORDID = "CLORDID";
    private static final String CUSIP = "CUSIP";

    @Test
    void shouldBeAbleToAcceptQuotedRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeCorrelation(1);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)0);

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, createdEvent.readRfqId());

        //user 2 quotes the RFQ
        final QuoteRfqCommand quoteRfqCommand = new QuoteRfqCommand();
        final DirectBuffer bufferQuote = new ExpandableArrayBuffer(QuoteRfqCommand.BUFFER_LENGTH);
        quoteRfqCommand.setBufferWriteHeader(bufferQuote, 0);
        quoteRfqCommand.writePrice(100);
        quoteRfqCommand.writeRfqId(createdEvent.readRfqId());
        quoteRfqCommand.writeResponderId(2);

        clusterProxy.clear();

        undertest.quoteRfq(quoteRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());
        final RfqQuotedEvent quotedEvent = new RfqQuotedEvent();
        quotedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);
        assertEquals(100, quotedEvent.readPrice());
        assertEquals(createdEvent.readRfqId(), quotedEvent.readRfqId());
        assertEquals(1, quotedEvent.readRequesterUserId());
        assertEquals(1, quotedEvent.readRfqQuoteId());
        assertEquals(2, quotedEvent.readResponderUserId());

        clusterProxy.clear();

        //user 1 accepts the RFQ quote
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        acceptRfqCommand.writeRfqId(createdEvent.readRfqId());
        acceptRfqCommand.writeRfqQuoteId(quotedEvent.readRfqQuoteId());
        acceptRfqCommand.writeUserId(1);

        undertest.acceptRfq(acceptRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqAcceptedEvent acceptedEvent = new RfqAcceptedEvent();
        acceptedEvent.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(100, acceptedEvent.readPrice());
        assertEquals(1, acceptedEvent.readAcceptedByUserId());
        assertEquals(1, acceptedEvent.readRequesterUserId());
        assertEquals(2, acceptedEvent.readResponderUserId());
    }

    @Test
    void shouldNotBeAbleToAcceptQuotedRfqDifferentIdToLatest()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)1);

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, createdEvent.readRfqId());

        //user 2 quotes the RFQ
        final QuoteRfqCommand quoteRfqCommand = new QuoteRfqCommand();
        final DirectBuffer bufferQuote = new ExpandableArrayBuffer(QuoteRfqCommand.BUFFER_LENGTH);
        quoteRfqCommand.setBufferWriteHeader(bufferQuote, 0);
        quoteRfqCommand.writePrice(100);
        quoteRfqCommand.writeRfqId(createdEvent.readRfqId());
        quoteRfqCommand.writeResponderId(2);

        clusterProxy.clear();

        undertest.quoteRfq(quoteRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());
        final RfqQuotedEvent quotedEvent = new RfqQuotedEvent();
        quotedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);
        assertEquals(100, quotedEvent.readPrice());
        assertEquals(createdEvent.readRfqId(), quotedEvent.readRfqId());
        assertEquals(1, quotedEvent.readRequesterUserId());
        assertEquals(1, quotedEvent.readRfqQuoteId());
        assertEquals(2, quotedEvent.readResponderUserId());

        clusterProxy.clear();

        //user 1 accepts the RFQ quote
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        acceptRfqCommand.writeRfqId(createdEvent.readRfqId());
        acceptRfqCommand.writeRfqQuoteId(33);
        acceptRfqCommand.writeUserId(1);

        undertest.acceptRfq(acceptRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Cannot accept RFQ", cancelEvent.readError());
    }

    @Test
    void shouldNotBeAbleToAcceptQuotedRfqDifferentUser()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)1);

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, createdEvent.readRfqId());

        //user 2 quotes the RFQ
        final QuoteRfqCommand quoteRfqCommand = new QuoteRfqCommand();
        final DirectBuffer bufferQuote = new ExpandableArrayBuffer(QuoteRfqCommand.BUFFER_LENGTH);
        quoteRfqCommand.setBufferWriteHeader(bufferQuote, 0);
        quoteRfqCommand.writePrice(100);
        quoteRfqCommand.writeRfqId(createdEvent.readRfqId());
        quoteRfqCommand.writeResponderId(2);

        clusterProxy.clear();

        undertest.quoteRfq(quoteRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());
        final RfqQuotedEvent quotedEvent = new RfqQuotedEvent();
        quotedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);
        assertEquals(100, quotedEvent.readPrice());
        assertEquals(createdEvent.readRfqId(), quotedEvent.readRfqId());
        assertEquals(1, quotedEvent.readRequesterUserId());
        assertEquals(1, quotedEvent.readRfqQuoteId());
        assertEquals(2, quotedEvent.readResponderUserId());

        clusterProxy.clear();

        //user 3 accepts the RFQ quote
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        acceptRfqCommand.writeRfqId(createdEvent.readRfqId());
        acceptRfqCommand.writeRfqQuoteId(33);
        acceptRfqCommand.writeUserId(3);

        undertest.acceptRfq(acceptRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Cannot accept RFQ, no relation to user", cancelEvent.readError());
    }

    @Test
    void shouldNotBeAbleToAcceptUnQuotedRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)1);

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, createdEvent.readRfqId());

        clusterProxy.clear();

        //user 3 accepts the RFQ quote
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        acceptRfqCommand.writeRfqId(createdEvent.readRfqId());
        acceptRfqCommand.writeRfqQuoteId(33);
        acceptRfqCommand.writeUserId(2);

        undertest.acceptRfq(acceptRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent invalidAccept = new RfqErrorEvent();
        invalidAccept.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Illegal transition", invalidAccept.readError());
    }

    @Test
    void shouldNotBeAbleToAcceptUnknownRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        //user 1 accepts the RFQ quote
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        acceptRfqCommand.writeRfqId(23678);
        acceptRfqCommand.writeRfqQuoteId(34234);
        acceptRfqCommand.writeCorrelation(123);
        acceptRfqCommand.writeUserId(1);

        undertest.acceptRfq(acceptRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Unknown RFQ", cancelEvent.readError());
    }

    @Test
    void shouldBeAbleToAcceptCounteredRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)0);

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, createdEvent.readRfqId());

        //user 2 quotes the RFQ
        final QuoteRfqCommand quoteRfqCommand = new QuoteRfqCommand();
        final DirectBuffer bufferQuote = new ExpandableArrayBuffer(QuoteRfqCommand.BUFFER_LENGTH);
        quoteRfqCommand.setBufferWriteHeader(bufferQuote, 0);
        quoteRfqCommand.writePrice(100);
        quoteRfqCommand.writeRfqId(createdEvent.readRfqId());
        quoteRfqCommand.writeResponderId(2);

        clusterProxy.clear();

        undertest.quoteRfq(quoteRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());
        final RfqQuotedEvent quotedEvent = new RfqQuotedEvent();
        quotedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);
        assertEquals(100, quotedEvent.readPrice());
        assertEquals(createdEvent.readRfqId(), quotedEvent.readRfqId());
        assertEquals(1, quotedEvent.readRequesterUserId());
        assertEquals(1, quotedEvent.readRfqQuoteId());
        assertEquals(2, quotedEvent.readResponderUserId());

        clusterProxy.clear();

        //user 1 counters the RFQ quote
        final CounterRfqCommand counterRfqCommand = new CounterRfqCommand();
        final DirectBuffer counterBuffer = new ExpandableArrayBuffer(CounterRfqCommand.BUFFER_LENGTH);
        counterRfqCommand.setBufferWriteHeader(counterBuffer, 0);
        counterRfqCommand.writeRfqId(createdEvent.readRfqId());
        counterRfqCommand.writePrice(99);
        counterRfqCommand.writeRfqQuoteId(quotedEvent.readRfqQuoteId());
        counterRfqCommand.writeUserId(1);

        undertest.counterRfq(counterRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqQuotedEvent quotedEventForCounter = new RfqQuotedEvent();
        quotedEventForCounter.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(99, quotedEventForCounter.readPrice());
        assertEquals(1, quotedEventForCounter.readRequesterUserId());
        assertEquals(quotedEvent.readRfqId(), quotedEventForCounter.readRfqId());
        assertEquals(2, quotedEventForCounter.readRfqQuoteId());
        assertEquals(2, quotedEventForCounter.readResponderUserId());


        clusterProxy.clear();

        //user 2 accepts the RFQ quote
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        acceptRfqCommand.writeRfqId(createdEvent.readRfqId());
        acceptRfqCommand.writeRfqQuoteId(quotedEventForCounter.readRfqQuoteId());
        acceptRfqCommand.writeUserId(2);

        undertest.acceptRfq(acceptRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqAcceptedEvent acceptedEvent = new RfqAcceptedEvent();
        acceptedEvent.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(99, acceptedEvent.readPrice());
        assertEquals(2, acceptedEvent.readAcceptedByUserId());
        assertEquals(123, acceptedEvent.readRfqRequesterCorrelationId());
        assertEquals(1, acceptedEvent.readRequesterUserId());
        assertEquals(2, acceptedEvent.readResponderUserId());
    }

    @SuppressWarnings("all")
    @Test
    void shouldBeAbleToAcceptCounteredCounteredRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeCorrelation(1);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)0);

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, createdEvent.readRfqId());

        //user 2 quotes the RFQ
        final QuoteRfqCommand quoteRfqCommand = new QuoteRfqCommand();
        final DirectBuffer bufferQuote = new ExpandableArrayBuffer(QuoteRfqCommand.BUFFER_LENGTH);
        quoteRfqCommand.setBufferWriteHeader(bufferQuote, 0);
        quoteRfqCommand.writePrice(100);
        quoteRfqCommand.writeRfqId(createdEvent.readRfqId());
        quoteRfqCommand.writeResponderId(2);

        clusterProxy.clear();

        undertest.quoteRfq(quoteRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());
        final RfqQuotedEvent quotedEvent = new RfqQuotedEvent();
        quotedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);
        assertEquals(100, quotedEvent.readPrice());
        assertEquals(createdEvent.readRfqId(), quotedEvent.readRfqId());
        assertEquals(1, quotedEvent.readRequesterUserId());
        assertEquals(1, quotedEvent.readRfqQuoteId());
        assertEquals(2, quotedEvent.readResponderUserId());

        clusterProxy.clear();

        //user 1 counters the RFQ quote
        final CounterRfqCommand counterRfqCommand = new CounterRfqCommand();
        final DirectBuffer counterBuffer = new ExpandableArrayBuffer(CounterRfqCommand.BUFFER_LENGTH);
        counterRfqCommand.setBufferWriteHeader(counterBuffer, 0);
        counterRfqCommand.writeRfqId(createdEvent.readRfqId());
        counterRfqCommand.writePrice(99);
        counterRfqCommand.writeRfqQuoteId(quotedEvent.readRfqQuoteId());
        counterRfqCommand.writeUserId(1);

        undertest.counterRfq(counterRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqQuotedEvent quotedEventForCounter = new RfqQuotedEvent();
        quotedEventForCounter.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(99, quotedEventForCounter.readPrice());
        assertEquals(1, quotedEventForCounter.readRequesterUserId());
        assertEquals(quotedEvent.readRfqId(), quotedEventForCounter.readRfqId());
        assertEquals(2, quotedEventForCounter.readRfqQuoteId());
        assertEquals(2, quotedEventForCounter.readResponderUserId());

        clusterProxy.clear();

        //user 2 counters user 1's
        final DirectBuffer counterCounterBuffer = new ExpandableArrayBuffer(CounterRfqCommand.BUFFER_LENGTH);
        counterRfqCommand.setBufferWriteHeader(counterCounterBuffer, 0);
        counterRfqCommand.writeRfqId(createdEvent.readRfqId());
        counterRfqCommand.writePrice(98);
        counterRfqCommand.writeRfqQuoteId(quotedEventForCounter.readRfqQuoteId());
        counterRfqCommand.writeUserId(2);

        undertest.counterRfq(counterRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqQuotedEvent quotedEventForCounterCounter = new RfqQuotedEvent();
        quotedEventForCounterCounter.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(98, quotedEventForCounterCounter.readPrice());
        assertEquals(1, quotedEventForCounterCounter.readRequesterUserId());
        assertEquals(quotedEvent.readRfqId(), quotedEventForCounterCounter.readRfqId());
        assertEquals(3, quotedEventForCounterCounter.readRfqQuoteId());
        assertEquals(2, quotedEventForCounterCounter.readResponderUserId());

        clusterProxy.clear();

        //user 1 accepts the RFQ counter
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        acceptRfqCommand.writeRfqId(createdEvent.readRfqId());
        acceptRfqCommand.writeRfqQuoteId(quotedEventForCounterCounter.readRfqQuoteId());
        acceptRfqCommand.writeUserId(1);

        undertest.acceptRfq(acceptRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqAcceptedEvent acceptedEvent = new RfqAcceptedEvent();
        acceptedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);
        assertEquals(RfqAcceptedEvent.EIDER_ID, EiderHelper.getEiderId(clusterProxy.getBroadcasts().get(0), 0));

        assertEquals(98, acceptedEvent.readPrice());
        assertEquals(1, acceptedEvent.readAcceptedByUserId());
        assertEquals(1, acceptedEvent.readRfqRequesterCorrelationId());
        assertEquals(1, acceptedEvent.readRequesterUserId());
        assertEquals(2, acceptedEvent.readResponderUserId());
    }


    Instruments buildInstruments()
    {
        final Instruments instruments = new Instruments();
        final DirectBuffer buffer = new ExpandableArrayBuffer(AddInstrumentCommand.BUFFER_LENGTH);
        final AddInstrumentCommand addInstrument = new AddInstrumentCommand();
        addInstrument.setBufferWriteHeader(buffer, 0);

        addInstrument.writeEnabled(true);
        addInstrument.writeCusip(CUSIP);
        addInstrument.writeMinSize(100);
        addInstrument.writeSecurityId(688);

        instruments.addInstrument(addInstrument, 0);

        addInstrument.writeEnabled(false);
        addInstrument.writeCusip("DISABLED");
        addInstrument.writeMinSize(100);
        addInstrument.writeSecurityId(789);

        instruments.addInstrument(addInstrument, 0);

        return instruments;
    }

}
