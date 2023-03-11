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

package com.aeroncookbook.cluster.integration;

import com.aeroncookbook.cluster.rfq.demuxer.SbeDemuxer;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentEncoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderEncoder;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentsIntegrationTest
{
    private static final String CUSIP_0001 = "CUSIP0001";
    private static final String CUSIP_0002 = "CUSIP0002";
    private static final String CUSIP_0003 = "CUSIP0003";
    private static final String IPC = "aeron:ipc";
    private static final int IPC_STREAM = 0;
    private final ExpandableDirectByteBuffer workingBuffer = new ExpandableDirectByteBuffer(100);
    private final MediaDriver.Context driverContext = new MediaDriver.Context()
        .sharedIdleStrategy(YieldingIdleStrategy.INSTANCE)
        .threadingMode(ThreadingMode.SHARED);

    @Test
    @Timeout(value = 15, unit = SECONDS)
    void canWriteReadFromSnapshot()
    {
        //prepare aeron
        final MediaDriver driver = MediaDriver.launch(driverContext);
        final Aeron aeron = Aeron.connect();

        final ExclusivePublication publication = aeron.addExclusivePublication(IPC, IPC_STREAM);
        final Subscription subscription = aeron.addSubscription(IPC, IPC_STREAM);

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
        final SbeDemuxer sourceDemuxer = new SbeDemuxer(null, source);

        final AddInstrumentEncoder instrumentCommand = new AddInstrumentEncoder();
        final MessageHeaderEncoder header = new MessageHeaderEncoder();
        header.wrap(workingBuffer, 0);
        instrumentCommand.wrapAndApplyHeader(workingBuffer, 0, header);
        instrumentCommand.securityId(1);
        instrumentCommand.cusip(CUSIP_0001);
        instrumentCommand.enabled(BooleanType.TRUE);
        instrumentCommand.minSize(10);

        //submit the instrument to the demuxer
        sourceDemuxer.onFragment(workingBuffer, 0, AddInstrumentEncoder.BLOCK_LENGTH, null);

        //ensure data is in the instruments
        assertTrue(source.isInstrumentEnabled(1));

        //write the snapshot to the publication
        source.snapshotTo(publication);

        //prep destination
        final Instruments destination = new Instruments();
        final SbeDemuxer destinationDemuxer = new SbeDemuxer(null, destination);

        int count;
        do
        {
            subscription.poll(destinationDemuxer, 1);
            count = destination.instrumentCount();
        }
        while (count == 0);

        //confirm the destination now has the data loaded from the snapshot
        assertEquals(1, destination.instrumentCount());
        assertTrue(destination.isInstrumentEnabled(1));

        //tidy up aeron
        quietClose(publication);
        quietClose(subscription);
        quietClose(aeron);
        quietClose(driver);
    }

    @Test
    void manyRecords()
    {
        //prepare aeron
        //write to the source of the snapshot
        final Instruments undertest = new Instruments();

        undertest.addInstrument(1, CUSIP_0001, true, 10);
        undertest.addInstrument(2, CUSIP_0002, true, 11);
        undertest.addInstrument(3, CUSIP_0003, false, 12);

        assertEquals(3, undertest.instrumentCount());
        assertTrue(undertest.isInstrumentEnabled(1));
        assertEquals(CUSIP_0001, undertest.byId(1).cusip());
        assertEquals(CUSIP_0002, undertest.byId(2).cusip());
        assertEquals(CUSIP_0003, undertest.byId(3).cusip());
        assertTrue(undertest.isInstrumentEnabled(2));
        assertFalse(undertest.isInstrumentEnabled(3));

    }
}
