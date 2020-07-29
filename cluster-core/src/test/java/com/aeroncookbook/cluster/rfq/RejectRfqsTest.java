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

package com.aeroncookbook.cluster.rfq;

import com.aeroncookbook.cluster.rfq.gen.CounterRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RejectRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RfqCreatedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqQuotedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqRejectedEvent;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RejectRfqsTest
{
    private static final String CLORDID = "CLORDID";
    private static final String CUSIP = "CUSIP";

    @Test
    void shouldBeAbleToRejectQuotedRfq()
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
        final RejectRfqCommand rejectRfqCommand = new RejectRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(RejectRfqCommand.BUFFER_LENGTH);
        rejectRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        rejectRfqCommand.writeRfqId(createdEvent.readRfqId());
        rejectRfqCommand.writeRfqQuoteId(quotedEvent.readRfqQuoteId());
        rejectRfqCommand.writeUserId(1);

        undertest.rejectRfq(rejectRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqRejectedEvent rejectedEvent = new RfqRejectedEvent();
        rejectedEvent.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(100, rejectedEvent.readPrice());
        assertEquals(1, rejectedEvent.readRejectedByUserId());
        assertEquals(123, rejectedEvent.readRequesterCorrelationId());
        assertEquals(1, rejectedEvent.readRequesterUserId());
        assertEquals(2, rejectedEvent.readResponderUserId());
    }

    @Test
    void shouldNotBeAbleToRejectQuotedRfqDifferentIdToLatest()
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

        //user 1 rejects the RFQ quote
        final RejectRfqCommand rejectRfqCommand = new RejectRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(RejectRfqCommand.BUFFER_LENGTH);
        rejectRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        rejectRfqCommand.writeRfqId(createdEvent.readRfqId());
        rejectRfqCommand.writeRfqQuoteId(33);
        rejectRfqCommand.writeUserId(1);

        undertest.rejectRfq(rejectRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Cannot reject RFQ", cancelEvent.readError());
    }

    @Test
    void shouldNotBeAbleToRejectQuotedRfqDifferentUser()
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

        //user 3 accepts the RFQ quote
        final RejectRfqCommand rejectRfqCommand = new RejectRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(RejectRfqCommand.BUFFER_LENGTH);
        rejectRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        rejectRfqCommand.writeRfqId(createdEvent.readRfqId());
        rejectRfqCommand.writeRfqQuoteId(33);
        rejectRfqCommand.writeUserId(3);

        undertest.rejectRfq(rejectRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Cannot reject RFQ, no relation to user", cancelEvent.readError());
    }

    @Test
    void shouldNotBeAbleToRejectUnQuotedRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeCorrelation(234);
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

        clusterProxy.clear();

        //user 3 accepts the RFQ quote
        final RejectRfqCommand rejectRfqCommand = new RejectRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(RejectRfqCommand.BUFFER_LENGTH);
        rejectRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        rejectRfqCommand.writeRfqId(createdEvent.readRfqId());
        rejectRfqCommand.writeRfqQuoteId(33);
        rejectRfqCommand.writeUserId(2);

        undertest.rejectRfq(rejectRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent invalidAccept = new RfqErrorEvent();
        invalidAccept.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Illegal transition", invalidAccept.readError());
    }

    @Test
    void shouldNotBeAbleToRejectUnknownRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        //user 1 accepts the RFQ quote
        final RejectRfqCommand rejectRfqCommand = new RejectRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(RejectRfqCommand.BUFFER_LENGTH);
        rejectRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        rejectRfqCommand.writeRfqId(23678);
        rejectRfqCommand.writeRfqQuoteId(34234);
        rejectRfqCommand.writeUserId(1);

        undertest.rejectRfq(rejectRfqCommand, 2L, 2L);
        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Unknown RFQ", cancelEvent.readError());
    }

    @Test
    void shouldBeAbleToRejectCounteredRfq()
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
        final RejectRfqCommand rejectRfqCommand = new RejectRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(RejectRfqCommand.BUFFER_LENGTH);
        rejectRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        rejectRfqCommand.writeRfqId(createdEvent.readRfqId());
        rejectRfqCommand.writeRfqQuoteId(quotedEventForCounter.readRfqQuoteId());
        rejectRfqCommand.writeUserId(2);

        undertest.rejectRfq(rejectRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqRejectedEvent rejectedEvent = new RfqRejectedEvent();
        rejectedEvent.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(99, rejectedEvent.readPrice());
        assertEquals(2, rejectedEvent.readRejectedByUserId());
        assertEquals(123, rejectedEvent.readRequesterCorrelationId());
        assertEquals(1, rejectedEvent.readRequesterUserId());
        assertEquals(2, rejectedEvent.readResponderUserId());
    }

    @Test
    void shouldBeAbleToRejectCounteredCounteredRfq()
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
        final CounterRfqCommand counterCounterRfqCommand = new CounterRfqCommand();
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
        final RejectRfqCommand rejectRfqCommand = new RejectRfqCommand();
        final DirectBuffer acceptBuffer = new ExpandableArrayBuffer(RejectRfqCommand.BUFFER_LENGTH);
        rejectRfqCommand.setBufferWriteHeader(acceptBuffer, 0);
        rejectRfqCommand.writeRfqId(createdEvent.readRfqId());
        rejectRfqCommand.writeRfqQuoteId(quotedEventForCounterCounter.readRfqQuoteId());
        rejectRfqCommand.writeUserId(1);

        undertest.rejectRfq(rejectRfqCommand, 2L, 2L);
        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqRejectedEvent rejectedEvent = new RfqRejectedEvent();
        rejectedEvent.setUnderlyingBuffer(clusterProxy.broadcasts.get(0), 0);
        assertEquals(98, rejectedEvent.readPrice());
        assertEquals(1, rejectedEvent.readRejectedByUserId());
        assertEquals(123, rejectedEvent.readRequesterCorrelationId());
        assertEquals(1, rejectedEvent.readRequesterUserId());
        assertEquals(2, rejectedEvent.readResponderUserId());
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
