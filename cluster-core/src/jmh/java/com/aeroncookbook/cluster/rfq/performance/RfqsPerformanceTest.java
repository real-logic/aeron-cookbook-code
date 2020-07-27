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

package com.aeroncookbook.cluster.rfq.performance;

import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

public class RfqsPerformanceTest
{
    public static final PerfTestClusterProxy proxy = new PerfTestClusterProxy();
    public static final Rfqs underTestCreate = new Rfqs(buildInstruments(), proxy, 1000000, 1);
    public static final Rfqs underTestQuote = new Rfqs(buildInstruments(), proxy, 1000000, 1);
    public static final CreateRfqCommand createRfqCommand = buildCreate();
    public static final QuoteRfqCommand quoteRfqCommand = buildQuote();

    @Benchmark
    public void createRfq(Blackhole bh)
    {
        //first million will be actual creates, after that error flow
        underTestCreate.createRfq(createRfqCommand, 1, 2L);
        bh.consume(proxy.getEiderIdReturned());
    }

    //@Benchmark
    public void quoteRfq(Blackhole bh)
    {
        //first million will be actual creates, after that error flow
        underTestQuote.createRfq(createRfqCommand, 1, 2L);
        //this will really only test the error flow - only first quote will exercise full flow.
        underTestQuote.quoteRfq(quoteRfqCommand, 1, 2L);
        bh.consume(proxy.getEiderIdReturned());
    }

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(RfqsPerformanceTest.class.getSimpleName())
            .forks(1)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .shouldDoGC(false)
            .warmupIterations(3)
            .addProfiler("gc")
            .build();

        new Runner(opt).run();
    }

    public static CreateRfqCommand buildCreate()
    {
        final CreateRfqCommand createRfqCommand = new CreateRfqCommand();
        final DirectBuffer buffer = new ExpandableArrayBuffer(CreateRfqCommand.BUFFER_LENGTH);
        createRfqCommand.setBufferWriteHeader(buffer, 0);
        createRfqCommand.writeClOrdId("CLORDID");
        createRfqCommand.writeCusip("CUSIP");
        createRfqCommand.writeUserId(1);
        createRfqCommand.writeExpireTimeMs(60_000);
        createRfqCommand.writeQuantity(200);
        createRfqCommand.writeSide("B");

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
}
