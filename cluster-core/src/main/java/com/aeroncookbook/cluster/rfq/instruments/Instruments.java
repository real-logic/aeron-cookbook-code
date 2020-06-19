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

package com.aeroncookbook.cluster.rfq.instruments;

import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instrument.gen.EnableInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instrument.gen.Instrument;
import com.aeroncookbook.cluster.rfq.instrument.gen.InstrumentRepository;
import com.aeroncookbook.cluster.rfq.instrument.gen.InstrumentSequence;

import com.aeroncookbook.cluster.rfq.util.Snapshotable;
import io.aeron.ExclusivePublication;
import io.aeron.cluster.service.ClientSession;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Instruments extends Snapshotable
{
    private static final int DEFAULT_MIN_VALUE = 0;

    private final InstrumentRepository instrumentRepository;
    private final InstrumentSequence instrumentSequence;
    private final Logger log = LoggerFactory.getLogger(Instruments.class);

    public Instruments()
    {
        instrumentRepository = InstrumentRepository.createWithCapacity(100);
        MutableDirectBuffer sequenceBuffer = new ExpandableArrayBuffer(InstrumentSequence.BUFFER_LENGTH);
        instrumentSequence = new InstrumentSequence();
        instrumentSequence.setUnderlyingBuffer(sequenceBuffer, 0);
    }

    public void addInstrument(AddInstrumentCommand addInstrument, long timestamp, ClientSession session)
    {
        int nextId = instrumentSequence.nextInstrumentIdSequence();
        final Instrument instrument = instrumentRepository.appendWithKey(nextId);
        if (instrument != null)
        {
            instrument.writeCusip(addInstrument.readCusip());
            instrument.writeMinLevel(addInstrument.readMinLevel());
            instrument.writeSecurityId(addInstrument.readSecurityId());
            instrument.writeEnabled(addInstrument.readEnabled());
        }
        else
        {
            log.info("Instrument repository is full. CUSIP {} ignored", addInstrument.readCusip());
        }
    }

    public void enableInstrument(EnableInstrumentCommand enableInstrument, long timestamp, ClientSession session)
    {
        List<Integer> withCusip = instrumentRepository.getAllWithIndexCusipValue(enableInstrument.readCusip());
        for (Integer offset : withCusip)
        {
            Instrument byBufferOffset = instrumentRepository.getByBufferOffset(offset);
            if (byBufferOffset != null)
            {
                byBufferOffset.writeEnabled(enableInstrument.readEnabled());
            }
        }
    }

    public boolean isInstrumentEnabled(String cusip)
    {
        List<Integer> withCusip = instrumentRepository.getAllWithIndexCusipValue(cusip);
        for (Integer offset : withCusip)
        {
            Instrument byBufferOffset = instrumentRepository.getByBufferOffset(offset);
            if (byBufferOffset != null)
            {
                return byBufferOffset.readEnabled();
            }
        }
        return false;
    }

    public int getMinValue(String cusip)
    {
        List<Integer> allWithIndexCusipValue = instrumentRepository.getAllWithIndexCusipValue(cusip);
        if (allWithIndexCusipValue.isEmpty())
        {
            return DEFAULT_MIN_VALUE;
        }
        final Instrument byBufferOffset = instrumentRepository.getByBufferOffset(allWithIndexCusipValue.get(0));
        if (byBufferOffset == null)
        {
            return DEFAULT_MIN_VALUE;
        }
        return byBufferOffset.readMinLevel();
    }

    public int instrumentCount()
    {
        return instrumentRepository.getCurrentCount();
    }

    public int instrumentCapacity()
    {
        return instrumentRepository.getCapacity();
    }

    public boolean knownCusip(String cusip)
    {
        return !instrumentRepository.getAllWithIndexCusipValue(cusip).isEmpty();
    }

    @Override
    public void snapshotTo(ExclusivePublication snapshotPublication)
    {
        for (int index = 0; index < instrumentRepository.getCurrentCount(); index++)
        {
            final int offset = instrumentRepository.getOffsetByBufferIndex(index);
            boolean success = reliableSnapshotOffer(snapshotPublication, instrumentRepository.getUnderlyingBuffer(),
                offset, Instrument.BUFFER_LENGTH);

            if (!success)
            {
                log.info("Could not offer instrument to snapshot publication");
            }
        }
    }

    @Override
    public void loadFromSnapshot(DirectBuffer buffer, int offset)
    {
        instrumentRepository.appendByCopyFromBuffer(buffer, offset);
    }
}
