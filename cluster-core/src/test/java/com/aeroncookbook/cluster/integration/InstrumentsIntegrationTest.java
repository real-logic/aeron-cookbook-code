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

package com.aeroncookbook.cluster.integration;

import com.aeroncookbook.cluster.rfq.demuxer.InstrumentDemuxer;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.CloseHelper.quietClose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstrumentsIntegrationTest
{
    private static final String CUSIP_0001 = "CUSIP0001";
    private static final String IPC = "aeron:ipc";
    private static final int IPC_STREAM = 0;
    private final ExpandableDirectByteBuffer workingBuffer = new ExpandableDirectByteBuffer(100);
    private final MediaDriver.Context driverContext = new MediaDriver.Context()
        .sharedIdleStrategy(YieldingIdleStrategy.INSTANCE)
        .threadingMode(ThreadingMode.SHARED);

    @Test
    @Timeout(value = 15, unit = SECONDS)
    public void canWriteReadFromSnapshot()
    {
        //prepare aeron
        final MediaDriver driver = MediaDriver.launch(driverContext);
        final Aeron aeron = Aeron.connect();

        ExclusivePublication publication = aeron.addExclusivePublication(IPC, IPC_STREAM);
        Subscription subscription = aeron.addSubscription(IPC, IPC_STREAM);

        //await connected
        while (!subscription.isConnected())
        {
            YieldingIdleStrategy.INSTANCE.idle();
        }

        while (!publication.isConnected())
        {
            YieldingIdleStrategy.INSTANCE.idle();
        }

        //write to the source of the snapshot
        final Instruments source = new Instruments();
        final InstrumentDemuxer sourceDemuxer = new InstrumentDemuxer(source);

        final AddInstrumentCommand instrumentCommand = new AddInstrumentCommand();
        instrumentCommand.setBufferWriteHeader(workingBuffer, 0);
        instrumentCommand.writeCusip(CUSIP_0001);
        instrumentCommand.writeMinLevel(10);
        instrumentCommand.writeSecurityId(1);
        instrumentCommand.writeEnabled(true);

        //submit the instrument to the demuxer
        sourceDemuxer.onFragment(workingBuffer, 0, AddInstrumentCommand.BUFFER_LENGTH, null);

        //ensure data is in the instruments
        assertTrue(source.knownCusip(CUSIP_0001));
        assertTrue(source.isInstrumentEnabled(CUSIP_0001));

        //write the snapshot to the publication
        source.snapshotTo(publication);

        //prep destination
        final Instruments destination = new Instruments();
        final InstrumentDemuxer destinationDemuxer = new InstrumentDemuxer(destination);

        int count;
        do
        {
            subscription.poll(destinationDemuxer, 1);
            count = destination.instrumentCount();
        }
        while (count == 0);

        //confirm the destination now has the data loaded from the snapshot
        assertEquals(1, destination.instrumentCount());
        assertTrue(destination.knownCusip(CUSIP_0001));
        assertTrue(destination.isInstrumentEnabled(CUSIP_0001));

        //tidy up aeron
        quietClose(publication);
        quietClose(subscription);
        quietClose(aeron);
        quietClose(driver);
    }
}
