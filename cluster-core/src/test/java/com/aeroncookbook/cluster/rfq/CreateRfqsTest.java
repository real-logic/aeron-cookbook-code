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
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.ClusterProxy;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateRfqsTest
{

    @Test
    void shouldCreateRfq()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("CLORDID");
        createRfqCommand.writeCusip("CUSIP");
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeLimitPrice(100);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final QuoteRequestEvent event = new QuoteRequestEvent();
        event.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals("S", event.readSide());
        assertEquals(60_000, event.readExpireTimeMs());
        assertEquals(200, event.readQuantity());
        assertEquals(1, event.readBroadcastExcludeUserId());
        assertEquals(688, event.readSecurityId());
    }

    @Test
    void shouldRunOutOfCapacity()
    {
        final int capacity = 1000;
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("CAPCLORDID");
        createRfqCommand.writeCusip("CUSIP");
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeLimitPrice(100);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        for (int i = 0; i <= capacity; i++)
        {
            undertest.createRfq(createRfqCommand, 1);
        }

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(1000, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent event = new RfqErrorEvent();
        event.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

        assertEquals("System at capacity", event.readError());
        assertEquals("CAPCLORDID", event.readClOrdId());
        assertEquals(-1, event.readRfqId());
    }

    @Test
    void shouldNotCreateRfqWithUnknownCusip()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("MCLORDID");
        createRfqCommand.writeCusip("MYSTERY");
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeLimitPrice(100);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1);

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
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("WSLORDID");
        createRfqCommand.writeCusip("CUSIP");
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeLimitPrice(100);
        createRfqCommand.writeQuantity(1);
        createRfqCommand.writeSide("B");

        undertest.createRfq(createRfqCommand, 1);

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
        addInstrument.writeCusip("CUSIP");
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

    class TestClusterProxy implements ClusterProxy
    {

        List<DirectBuffer> replies = new ArrayList<>();
        List<DirectBuffer> broadcasts = new ArrayList<>();

        @Override
        public void reply(DirectBuffer buffer, int offset, int length)
        {
            MutableDirectBuffer toAdd = new ExpandableArrayBuffer(length - offset);
            buffer.getBytes(offset, toAdd, 0, length);
            replies.add(buffer);
        }

        @Override
        public void broadcast(DirectBuffer buffer, int offset, int length)
        {
            MutableDirectBuffer toAdd = new ExpandableArrayBuffer(length - offset);
            buffer.getBytes(offset, toAdd, 0, length);
            broadcasts.add(buffer);
        }

        public List<DirectBuffer> getReplies()
        {
            return replies;
        }

        public List<DirectBuffer> getBroadcasts()
        {
            return broadcasts;
        }
    }
}
