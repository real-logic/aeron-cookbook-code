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

import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RfqCreatedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqExpiredEvent;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateRfqsTest
{
    private static final String CLORDID = "CLORDID";
    private static final String CUSIP = "CUSIP";

    @Test
    void shouldCreateRfqBuy()
    {
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

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent rfqCreatedEvent = new RfqCreatedEvent();
        rfqCreatedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(123, rfqCreatedEvent.readRfqRequesterCorrelationId());
        assertEquals(1, rfqCreatedEvent.readRfqId());

        assertEquals(0, rfqCreatedEvent.readSide());
        assertEquals(60_000, rfqCreatedEvent.readExpireTimeMs());
        assertEquals(200, rfqCreatedEvent.readQuantity());
        assertEquals(1, rfqCreatedEvent.readRfqRequesterUserId());
        assertEquals(688, rfqCreatedEvent.readSecurityId());
        assertEquals(1, rfqCreatedEvent.readRfqId());
    }

    @Test
    void shouldCreateRfqSell()
    {
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

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent rfqCreatedEvent = new RfqCreatedEvent();
        rfqCreatedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, rfqCreatedEvent.readSide());
        assertEquals(60_000, rfqCreatedEvent.readExpireTimeMs());
        assertEquals(200, rfqCreatedEvent.readQuantity());
        assertEquals(1, rfqCreatedEvent.readRfqRequesterUserId());
        assertEquals(688, rfqCreatedEvent.readSecurityId());
        assertEquals(1, rfqCreatedEvent.readRfqId());
    }

    @Test
    void shouldExpireIfClockMovedForward()
    {
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

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());

        final RfqCreatedEvent rfqCreatedEvent = new RfqCreatedEvent();
        rfqCreatedEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, rfqCreatedEvent.readSide());
        assertEquals(60_000, rfqCreatedEvent.readExpireTimeMs());
        assertEquals(200, rfqCreatedEvent.readQuantity());
        assertEquals(1, rfqCreatedEvent.readRfqRequesterUserId());
        assertEquals(688, rfqCreatedEvent.readSecurityId());
        assertEquals(1, rfqCreatedEvent.readRfqId());

        clusterProxy.clear();

        final List<TestClusterProxy.ExpiryTask> expiryTasks = clusterProxy.expiryTasksAt(60000);
        assertEquals(1, expiryTasks.size());
        for (final TestClusterProxy.ExpiryTask task : expiryTasks)
        {
            undertest.expire(task.rfqId);
        }

        assertEquals(0, clusterProxy.getReplies().size());
        assertEquals(1, clusterProxy.getBroadcasts().size());
        final RfqExpiredEvent rfqExpiredEvent = new RfqExpiredEvent();
        rfqExpiredEvent.setUnderlyingBuffer(clusterProxy.getBroadcasts().get(0), 0);

        assertEquals(1, rfqExpiredEvent.readRfqId());
    }

    @Test
    void shouldRunOutOfCapacity()
    {
        final int capacity = 1000;
        final LastMessageTestClusterProxy clusterProxy = new LastMessageTestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, capacity, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)0);

        for (int i = 0; i <= capacity; i++)
        {
            undertest.createRfq(createRfqCommand, 1, 2L);
        }

        assertEquals(1, clusterProxy.getReplyCount());
        assertEquals(capacity, clusterProxy.getBroadcastCount());

        final RfqErrorEvent event = new RfqErrorEvent();
        event.setUnderlyingBuffer(clusterProxy.getLastReply(), 0);

        assertEquals("System at capacity", event.readError());
        assertEquals(123, event.readCorrelation());
        assertEquals(-1, event.readRfqId());
    }

    @Test
    void shouldNotCreateRfqWithUnknownCusip()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeInstrumentId(23);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)0);

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent event = new RfqErrorEvent();
        event.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

        assertEquals("Unknown CUSIP", event.readError());
        assertEquals(123, event.readCorrelation());
        assertEquals(-1, event.readRfqId());
    }

    @Test
    void shouldValidateSize()
    {
        final TestClusterProxy clusterProxy = new TestClusterProxy();
        final Rfqs undertest = new Rfqs(buildInstruments(), clusterProxy, 1, 200);

        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(1);
        createRfqCommand.writeSide((short)0);

        undertest.createRfq(createRfqCommand, 1, 2L);

        assertEquals(1, clusterProxy.getReplies().size());
        assertEquals(0, clusterProxy.getBroadcasts().size());

        final RfqErrorEvent event = new RfqErrorEvent();
        event.setUnderlyingBuffer(clusterProxy.getReplies().get(0), 0);

        assertEquals("RFQ is for smaller quantity than instrument min size", event.readError());
        assertEquals(123, event.readCorrelation());
        assertEquals(-1, event.readRfqId());
    }


    Instruments buildInstruments()
    {
        final Instruments instruments = new Instruments();
        instruments.addInstrument(688, CUSIP, true, 100);
        instruments.addInstrument(789, "DISABLED", false, 100);
        return instruments;
    }

}
