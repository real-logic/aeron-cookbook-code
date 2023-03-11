/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.cluster.rfq.performance;

import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class RfqsPerformanceTest
{
    public static final PerfTestClusterProxy PROXY = new PerfTestClusterProxy();
    public static final ReadingClusterProxy LAST_PROXY = new ReadingClusterProxy();
    public static final CreateRfqCommand CREATE_RFQ_COMMAND = buildCreate();
    public static final QuoteRfqCommand QUOTE_RFQ_COMMAND = buildQuote();
    public static final AcceptRfqCommand ACCEPT_RFQ_COMMAND = buildAccept();
    public static Rfqs underTestCreate = new Rfqs(buildInstruments(), PROXY, 1000000, 1);
    public static Rfqs underTestQuote = new Rfqs(buildInstruments(), LAST_PROXY, 1000000, 1);

    public static CreateRfqCommand buildCreate()
    {
        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeInstrumentId(1);
        createRfqCommand.writeCorrelation(123);
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide((short)0);

        return createRfqCommand;
    }

    public static QuoteRfqCommand buildQuote()
    {
        final QuoteRfqCommand quoteRfqCommand = new QuoteRfqCommand();
        final DirectBuffer bufferQuote = new ExpandableArrayBuffer(QuoteRfqCommand.BUFFER_LENGTH);
        quoteRfqCommand.setBufferWriteHeader(bufferQuote, 0);
        quoteRfqCommand.writePrice(100);
        quoteRfqCommand.writeRfqId(1);
        quoteRfqCommand.writeResponderId(20);

        return quoteRfqCommand;
    }

    private static AcceptRfqCommand buildAccept()
    {
        final AcceptRfqCommand acceptRfqCommand = new AcceptRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(AcceptRfqCommand.BUFFER_LENGTH);
        acceptRfqCommand.setBufferWriteHeader(buffer, 0);
        acceptRfqCommand.writeUserId(1);
        return acceptRfqCommand;
    }

    public static Instruments buildInstruments()
    {
        final Instruments instruments = new Instruments();
        instruments.addInstrument(688, "CUSIP", true, 100);
        instruments.addInstrument(789, "DISABLED", false, 100);
        return instruments;
    }

    @Benchmark
    public void createRfq(final Blackhole bh)
    {
        //first million will be actual creates, after that error flow
        underTestCreate.createRfq(CREATE_RFQ_COMMAND, 1, 2L);
        bh.consume(PROXY.getEiderIdReturned());
    }

    @Benchmark
    public void workflow(final Blackhole bh)
    {
        underTestQuote.createRfq(CREATE_RFQ_COMMAND, 1, 2L);

        QUOTE_RFQ_COMMAND.writeRfqId(LAST_PROXY.getLastRfqId());
        underTestQuote.quoteRfq(QUOTE_RFQ_COMMAND, 2, 2L);

        ACCEPT_RFQ_COMMAND.writeRfqId(LAST_PROXY.getLastRfqId());
        ACCEPT_RFQ_COMMAND.writeRfqQuoteId(LAST_PROXY.getLastRfqQuoteId());
        underTestQuote.acceptRfq(ACCEPT_RFQ_COMMAND, 3, 2L);

        bh.consume(PROXY.getEiderIdReturned());
    }

    @Setup(Level.Iteration)
    public void resetRfqs()
    {
        underTestCreate = new Rfqs(buildInstruments(), PROXY, 1000000, 1);
        underTestQuote = new Rfqs(buildInstruments(), LAST_PROXY, 1000000, 1);
    }
}
