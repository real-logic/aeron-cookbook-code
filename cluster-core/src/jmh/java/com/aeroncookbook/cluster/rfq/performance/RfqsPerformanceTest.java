/*
 * Copyright 2019-2022 Shaun Laurens.
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

package com.aeroncookbook.cluster.rfq.performance;

import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class RfqsPerformanceTest
{
    public static final PerfTestClusterProxy proxy = new PerfTestClusterProxy();
    public static final ReadingClusterProxy lastProxy = new ReadingClusterProxy();
    public static final CreateRfqCommand createRfqCommand = buildCreate();
    public static final QuoteRfqCommand quoteRfqCommand = buildQuote();
    public static final AcceptRfqCommand acceptRfqCommand = buildAccept();
    public static Rfqs underTestCreate = new Rfqs(buildInstruments(), proxy, 1000000, 1);
    public static Rfqs underTestQuote = new Rfqs(buildInstruments(), lastProxy, 1000000, 1);

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(RfqsPerformanceTest.class.getSimpleName())
            .forks(1)
            .mode(Mode.SampleTime)
            .timeUnit(TimeUnit.MICROSECONDS)
            .shouldDoGC(true)
            .warmupIterations(10)
            .warmupTime(TimeValue.seconds(1))
            .measurementIterations(5)
            .measurementTime(TimeValue.seconds(8))
            .addProfiler("gc")
            .build();

        new Runner(opt).run();
    }

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

    @Benchmark
    public void createRfq(Blackhole bh)
    {
        //first million will be actual creates, after that error flow
        underTestCreate.createRfq(createRfqCommand, 1, 2L);
        bh.consume(proxy.getEiderIdReturned());
    }

    @Benchmark
    public void workflow(Blackhole bh)
    {
        underTestQuote.createRfq(createRfqCommand, 1, 2L);

        quoteRfqCommand.writeRfqId(lastProxy.getLastRfqId());
        underTestQuote.quoteRfq(quoteRfqCommand, 2, 2L);

        acceptRfqCommand.writeRfqId(lastProxy.getLastRfqId());
        acceptRfqCommand.writeRfqQuoteId(lastProxy.getLastRfqQuoteId());
        underTestQuote.acceptRfq(acceptRfqCommand, 3, 2L);

        bh.consume(proxy.getEiderIdReturned());
    }

    @Setup(Level.Iteration)
    public void resetRfqs()
    {
        underTestCreate = new Rfqs(buildInstruments(), proxy, 1000000, 1);
        underTestQuote = new Rfqs(buildInstruments(), lastProxy, 1000000, 1);
    }
}
