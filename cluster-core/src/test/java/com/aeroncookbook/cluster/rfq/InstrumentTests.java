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

import com.aeroncookbook.cluster.rfq.demuxer.InstrumentDemuxer;
import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instrument.gen.EnableInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import org.agrona.ExpandableDirectByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstrumentTests
{
    private static final String CUSIP_0001 = "CUSIP0001";
    private static final String CUSIP_0002 = "CUSIP0002";
    private final ExpandableDirectByteBuffer workingBuffer = new ExpandableDirectByteBuffer(100);

    @Test
    public void canAddInstrument()
    {
        final Instruments underTest = new Instruments();
        final InstrumentDemuxer demuxer = new InstrumentDemuxer(underTest);

        final AddInstrumentCommand instrumentCommand = new AddInstrumentCommand();
        instrumentCommand.setBufferWriteHeader(workingBuffer, 0);
        instrumentCommand.writeCusip(CUSIP_0001);
        instrumentCommand.writeMinSize(10);
        instrumentCommand.writeSecurityId(1);
        instrumentCommand.writeEnabled(true);

        demuxer.onFragment(workingBuffer, 0, AddInstrumentCommand.BUFFER_LENGTH, null);

        assertTrue(underTest.isInstrumentEnabled(1));
        assertEquals(10, underTest.getMinSize(1));
        assertEquals(0, underTest.getMinSize(2));
    }

    @Test
    public void canDisableInstrument()
    {
        final Instruments underTest = new Instruments();
        final InstrumentDemuxer demuxer = new InstrumentDemuxer(underTest);

        final AddInstrumentCommand instrumentCommand = new AddInstrumentCommand();
        instrumentCommand.setBufferWriteHeader(workingBuffer, 0);
        instrumentCommand.writeCusip(CUSIP_0001);
        instrumentCommand.writeMinSize(10);
        instrumentCommand.writeSecurityId(1);
        instrumentCommand.writeEnabled(true);

        demuxer.onFragment(workingBuffer, 0, AddInstrumentCommand.BUFFER_LENGTH, null);

        assertNotNull(underTest.byId(1));
        assertTrue(underTest.isInstrumentEnabled(1));

        final EnableInstrumentCommand enableInstrumentCommand = new EnableInstrumentCommand();
        enableInstrumentCommand.setBufferWriteHeader(workingBuffer, 0);
        enableInstrumentCommand.writeCusip(CUSIP_0001);
        enableInstrumentCommand.writeEnabled(false);

        demuxer.onFragment(workingBuffer, 0, EnableInstrumentCommand.BUFFER_LENGTH, null);

        assertNotNull(underTest.byId(1));
        assertFalse(underTest.isInstrumentEnabled(1));
    }

}
