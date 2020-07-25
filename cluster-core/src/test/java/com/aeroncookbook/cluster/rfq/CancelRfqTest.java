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

import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CancelRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RfqAcceptedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqCanceledEvent;
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

class CancelRfqTest
{

    private static final String CLORDID = "CLORDID";
    static final String CUSIP = "CUSIP";

    @Test
    void shouldNotAllowCancelSomeoneElsesRfq()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer createRfqBuffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(createRfqBuffer, 0);
        createRfqCommand.writeClOrdId(CLORDID);
        createRfqCommand.writeCusip(CUSIP);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals(1, createdEvent.readRfqId());

        final CancelRfqCommand cancelRfqCommand = new CancelRfqCommand();
        final DirectBuffer cancelRfqBuffer = new ExpandableArrayBuffer(CancelRfqCommand.BUFFER_LENGTH);
        cancelRfqCommand.setBufferWriteHeader(cancelRfqBuffer, 0);
        cancelRfqCommand.writeRfqId(createdEvent.readRfqId());
        cancelRfqCommand.writeUserId(2);

        clusterProxy.clear();
        undertest.cancelRfq(cancelRfqCommand, 3L);

        assertEquals(1, clusterProxy.getReplies().size());
        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Cannot cancel RFQ, no relation to user", cancelEvent.readError());
    }

    @Test
    void shouldAllowCancelMyRfq()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer createRfqBuffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(createRfqBuffer, 0);
        createRfqCommand.writeClOrdId(CLORDID);
        createRfqCommand.writeCusip(CUSIP);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals(1, createdEvent.readRfqId());

        final CancelRfqCommand cancelRfqCommand = new CancelRfqCommand();
        final DirectBuffer cancelRfqBuffer = new ExpandableArrayBuffer(CancelRfqCommand.BUFFER_LENGTH);
        cancelRfqCommand.setBufferWriteHeader(cancelRfqBuffer, 0);
        cancelRfqCommand.writeRfqId(createdEvent.readRfqId());
        cancelRfqCommand.writeUserId(1);

        clusterProxy.clear();
        undertest.cancelRfq(cancelRfqCommand, 3L);

        assertEquals(1, clusterProxy.getReplies().size());
        final RfqCanceledEvent cancelEvent = new RfqCanceledEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals(CLORDID, cancelEvent.readClOrdId());
        assertEquals(createdEvent.readRfqId(), cancelEvent.readRfqId());
    }

    @Test
    void shouldNotAllowCancelingUnknownRfq()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1);

        final CancelRfqCommand cancelRfqCommand = new CancelRfqCommand();
        final DirectBuffer cancelRfqBuffer = new ExpandableArrayBuffer(CancelRfqCommand.BUFFER_LENGTH);
        cancelRfqCommand.setBufferWriteHeader(cancelRfqBuffer, 0);
        cancelRfqCommand.writeRfqId(23897);
        cancelRfqCommand.writeUserId(1);

        clusterProxy.clear();
        undertest.cancelRfq(cancelRfqCommand, 3L);

        assertEquals(1, clusterProxy.getReplies().size());
        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals("Unknown RFQ", cancelEvent.readError());
        assertEquals(23897, cancelEvent.readRfqId());
    }

    @Test
    void shouldNotBeAbleToCancelAcceptedRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId(CLORDID);
        createRfqCommand.writeCusip(CUSIP);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

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
        assertEquals(1, quotedEvent.readRequesterId());
        assertEquals(1, quotedEvent.readRfqQuoteId());
        assertEquals(2, quotedEvent.readResponderId());

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
        assertEquals("CLORDID", acceptedEvent.readRequesterClOrdId());
        assertEquals(1, acceptedEvent.readRequesterUserId());
        assertEquals(2, acceptedEvent.readResponderUserId());

        //user 1 attempts to cancel the RFQ Accept
        final CancelRfqCommand cancelRfqCommand = new CancelRfqCommand();
        final DirectBuffer cancelRfqBuffer = new ExpandableArrayBuffer(CancelRfqCommand.BUFFER_LENGTH);
        cancelRfqCommand.setBufferWriteHeader(cancelRfqBuffer, 0);
        cancelRfqCommand.writeRfqId(quotedEvent.readRfqId());
        cancelRfqCommand.writeUserId(1);

        clusterProxy.clear();
        undertest.cancelRfq(cancelRfqCommand, 3L);

        assertEquals(1, clusterProxy.getReplies().size());
        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals(RfqErrorEvent.EIDER_ID, EiderHelper.getEiderId(clusterProxy.getReplies().get(0), 0));
        assertEquals("Illegal transition", cancelEvent.readError());
        assertEquals(1, cancelEvent.readRfqId());
    }

    @Test
    void shouldNotBeAbleToCancelQuotedRfq()
    {
        //user 1 creates RFQ
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId(CLORDID);
        createRfqCommand.writeCusip(CUSIP);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1L, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

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
        assertEquals(1, quotedEvent.readRequesterId());
        assertEquals(1, quotedEvent.readRfqQuoteId());
        assertEquals(2, quotedEvent.readResponderId());

        clusterProxy.clear();

        //user 1 attempts to cancel the RFQ after quote
        final CancelRfqCommand cancelRfqCommand = new CancelRfqCommand();
        final DirectBuffer cancelRfqBuffer = new ExpandableArrayBuffer(CancelRfqCommand.BUFFER_LENGTH);
        cancelRfqCommand.setBufferWriteHeader(cancelRfqBuffer, 0);
        cancelRfqCommand.writeRfqId(quotedEvent.readRfqId());
        cancelRfqCommand.writeUserId(1);

        clusterProxy.clear();
        undertest.cancelRfq(cancelRfqCommand, 3L);

        assertEquals(1, clusterProxy.getReplies().size());
        final RfqErrorEvent cancelEvent = new RfqErrorEvent();
        cancelEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);
        assertEquals(RfqErrorEvent.EIDER_ID, EiderHelper.getEiderId(clusterProxy.getReplies().get(0), 0));
        assertEquals("Illegal transition", cancelEvent.readError());
        assertEquals(1, cancelEvent.readRfqId());
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
