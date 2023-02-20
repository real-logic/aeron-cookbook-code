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
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;

public class EncodingPerformanceTest
{
    public static final CounterRfqCommand COUNTER = buildAccept();

    public static CounterRfqCommand buildAccept()
    {
        final CounterRfqCommand counterRfqCommand = new CounterRfqCommand();
        final DirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(CreateRfqCommand.BUFFER_LENGTH));
        counterRfqCommand.setBufferWriteHeader(buffer, 0);
        return counterRfqCommand;
    }

    @Benchmark
    public void createRfqRoundtrip(final Blackhole bh)
    {
        COUNTER.writeRfqId(200);
        COUNTER.writeRfqQuoteId(1);
        COUNTER.writeUserId(1);
        COUNTER.writePrice(60_000);

        bh.consume(COUNTER.readRfqId());
        bh.consume(COUNTER.readRfqQuoteId());
        bh.consume(COUNTER.readUserId());
        bh.consume(COUNTER.readPrice());
    }


}
