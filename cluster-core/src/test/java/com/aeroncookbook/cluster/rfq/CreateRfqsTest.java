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

import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRequestEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqCreatedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateRfqsTest
{
    private static final String CLORDID = "CLORDID";
    private static final String CUSIP = "CUSIP";

    @Test
    void shouldCreateRfqBuy()
    {
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

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final QuoteRequestEvent quoteRequestEvent = new QuoteRequestEvent();
        quoteRequestEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

        assertEquals(CLORDID, createdEvent.readClOrdId());
        assertEquals(1, createdEvent.readRfqId());

        assertEquals("S", quoteRequestEvent.readSide());
        assertEquals(60_000, quoteRequestEvent.readExpireTimeMs());
        assertEquals(200, quoteRequestEvent.readQuantity());
        assertEquals(1, quoteRequestEvent.readBroadcastExcludeUserId());
        assertEquals(688, quoteRequestEvent.readSecurityId());
        assertEquals(1, quoteRequestEvent.readRfqId());
    }

    @Test
    void shouldCreateRfqSell()
    {
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
        createRfqCommand.writeSide("S");

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final QuoteRequestEvent quoteRequestEvent = new QuoteRequestEvent();
        quoteRequestEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        final RfqCreatedEvent createdEvent = new RfqCreatedEvent();
        createdEvent.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

        assertEquals(CLORDID, createdEvent.readClOrdId());
        assertEquals(1, createdEvent.readRfqId());

        assertEquals("B", quoteRequestEvent.readSide());
        assertEquals(60_000, quoteRequestEvent.readExpireTimeMs());
        assertEquals(200, quoteRequestEvent.readQuantity());
        assertEquals(1, quoteRequestEvent.readBroadcastExcludeUserId());
        assertEquals(688, quoteRequestEvent.readSecurityId());
        assertEquals(1, quoteRequestEvent.readRfqId());
    }

    @Test
    void shouldRunOutOfCapacity()
    {
        final int capacity = 50000;
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, capacity);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("CAPCLORDID");
        createRfqCommand.writeCusip(CUSIP);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        final long startTime = System.nanoTime();
        for (int i = 0; i <= capacity; i++)
        {
            undertest.createRfq(createRfqCommand, 1, 2L);
        }
        final long endTime = System.nanoTime();
        final long rateNs = (endTime - startTime) / capacity;
        System.out.println("Creation rate for " + capacity + " rfqs: " + rateNs + "ns per rfq");

        assertEquals(capacity + 1, clusterProxy.getReplies().size());
        assertEquals(capacity, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent event = new RfqErrorEvent();
        event.setUnderlyingBuffer(clusterProxy.getReplies().get(capacity), 0);

        assertEquals("System at capacity", event.readError());
        assertEquals("CAPCLORDID", event.readClOrdId());
        assertEquals(-1, event.readRfqId());
    }

    @Test
    void shouldNotCreateRfqWithUnknownCusip()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("MCLORDID");
        createRfqCommand.writeCusip("MYSTERY");
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent event = new RfqErrorEvent();
        event.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

        assertEquals("Unknown CUSIP", event.readError());
        assertEquals("MCLORDID", event.readClOrdId());
        assertEquals(-1, event.readRfqId());
    }

    @Test
    void shouldValidateSize()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("WSLORDID");
        createRfqCommand.writeCusip(CUSIP);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(1);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent event = new RfqErrorEvent();
        event.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

        assertEquals("RFQ is for smaller quantity than instrument min size", event.readError());
        assertEquals("WSLORDID", event.readClOrdId());
        assertEquals(-1, event.readRfqId());
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
