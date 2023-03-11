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

package com.aeroncookbook.cluster.rfq.instruments;

import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordDecoder;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordEncoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderEncoder;
import com.aeroncookbook.cluster.rfq.util.Snapshotable;
import io.aeron.ExclusivePublication;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Instruments extends Snapshotable
{
    private static final int DEFAULT_MIN_VALUE = 0;

    private final InstrumentSbeRepository instrumentRepository;
    private final InstrumentSequence instrumentSequence;
    private final Logger log = LoggerFactory.getLogger(Instruments.class);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    public Instruments()
    {
        instrumentRepository = InstrumentSbeRepository.createWithCapacity(100);
        instrumentSequence = InstrumentSequence.getInstance();
    }

    public void addInstrument(final int securityId, final String cusip, final boolean enabled, final int minSize)
    {
        final int nextId = instrumentSequence.nextInstrumentIdSequence();
        if (!instrumentRepository.appendWithKey(nextId, securityId, cusip, enabled, minSize))
        {
            log.info("Instrument repository is full. CUSIP {} ignored", cusip);
        }
    }

    public void setEnabledFlagForCusip(final String cusip, final boolean enabled)
    {
        final List<Integer> withCusip = instrumentRepository.getAllWithIndexCusipValue(cusip);
        for (final Integer offset : withCusip)
        {
            log.info("Setting enabled for instrument with CUSIP {} to {}", cusip, enabled);
            instrumentRepository.setEnabledFlagForOffset(offset, enabled);
        }
    }

    public boolean isInstrumentEnabled(final int instrumentId)
    {
        final Instrument instrument = instrumentRepository.getByKey(instrumentId);
        if (instrument == null)
        {
            return false;
        }
        return instrument.enabled();
    }

    public int getMinSize(final int instrumentId)
    {
        final Instrument instrument = instrumentRepository.getByKey(instrumentId);
        if (instrument == null)
        {
            return DEFAULT_MIN_VALUE;
        }
        return instrument.minSize();
    }

    public Instrument byId(final int instrumentId)
    {
        return instrumentRepository.getByKey(instrumentId);
    }

    public int instrumentCount()
    {
        return instrumentRepository.getCurrentCount();
    }

    @Override
    public void snapshotTo(final ExclusivePublication snapshotPublication)
    {
        final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(1024);
        final InstrumentRecordEncoder snapshotEncoder = new InstrumentRecordEncoder();
        for (int index = 0; index < instrumentRepository.getCurrentCount(); index++)
        {
            final Instrument byBufferIndex = instrumentRepository.getByBufferIndex(index);
            System.out.println("Snapshotting instrument " + byBufferIndex);
            messageHeaderEncoder.wrap(buffer, 0);
            snapshotEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
            snapshotEncoder.id(byBufferIndex.id());
            snapshotEncoder.securityId(byBufferIndex.securityId());
            snapshotEncoder.cusip(byBufferIndex.cusip());
            snapshotEncoder.enabled(byBufferIndex.enabled() ? BooleanType.TRUE : BooleanType.FALSE);
            snapshotEncoder.minSize(byBufferIndex.minSize());
            final boolean success = reliableSnapshotOffer(snapshotPublication,
                buffer, 0, messageHeaderEncoder.encodedLength() + snapshotEncoder.encodedLength());

            if (!success)
            {
                log.info("Could not offer instrument to snapshot publication");
            }
        }
    }

    @Override
    public void loadFromSnapshot(final DirectBuffer buffer, final int offset)
    {
        final InstrumentRecordDecoder decoder = new InstrumentRecordDecoder();
        messageHeaderDecoder.wrap(buffer, offset);
        decoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        instrumentRepository.appendWithKey(
            decoder.id(),
            decoder.securityId(),
            decoder.cusip(),
            decoder.enabled() == BooleanType.TRUE,
            decoder.minSize());
    }

}
