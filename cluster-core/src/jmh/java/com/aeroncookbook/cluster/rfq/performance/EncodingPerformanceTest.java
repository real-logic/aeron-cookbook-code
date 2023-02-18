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

package com.aeroncookbook.cluster.rfq.performance;

import com.aeroncookbook.cluster.rfq.gen.CounterRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class EncodingPerformanceTest
{
    public static final CounterRfqCommand counter = buildAccept();

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(EncodingPerformanceTest.class.getSimpleName())
            .forks(3)
            .mode(Mode.SampleTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .shouldDoGC(true)
            .warmupIterations(10)
            .warmupTime(TimeValue.seconds(1))
            .measurementIterations(5)
            .measurementTime(TimeValue.seconds(8))
            .addProfiler("gc")
            .build();

        new Runner(opt).run();
    }

    public static CounterRfqCommand buildAccept()
    {
        final CounterRfqCommand counterRfqCommand = new CounterRfqCommand();
        final DirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(CreateRfqCommand.BUFFER_LENGTH));
        counterRfqCommand.setBufferWriteHeader(buffer, 0);
        return counterRfqCommand;
    }

    @Benchmark
    public void createRfqRoundtrip(Blackhole bh)
    {
        counter.writeRfqId(200);
        counter.writeRfqQuoteId(1);
        counter.writeUserId(1);
        counter.writePrice(60_000);

        bh.consume(counter.readRfqId());
        bh.consume(counter.readRfqQuoteId());
        bh.consume(counter.readUserId());
        bh.consume(counter.readPrice());
    }


}
