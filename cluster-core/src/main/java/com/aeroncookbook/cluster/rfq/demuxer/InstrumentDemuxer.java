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

package com.aeroncookbook.cluster.rfq.demuxer;

import com.aeroncookbook.cluster.rfq.instrument.gen.AddInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instrument.gen.EnableInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instrument.gen.Instrument;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.util.EiderHelper.getEiderId;

public class InstrumentDemuxer implements FragmentHandler
{
    private final Instruments instruments;
    private final AddInstrumentCommand instrumentCommand;
    private final EnableInstrumentCommand enableInstrumentCommand;
    private final Logger log = LoggerFactory.getLogger(InstrumentDemuxer.class);
    private ClientSession session;
    private long timestamp;

    public InstrumentDemuxer(Instruments instruments)
    {
        this.instruments = instruments;
        this.instrumentCommand = new AddInstrumentCommand();
        this.enableInstrumentCommand = new EnableInstrumentCommand();
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);
        switch (eiderId)
        {
            case AddInstrumentCommand.EIDER_ID:
                instrumentCommand.setUnderlyingBuffer(buffer, offset);
                instruments.addInstrument(instrumentCommand, timestamp, session);
                break;
            case EnableInstrumentCommand.EIDER_ID:
                enableInstrumentCommand.setUnderlyingBuffer(buffer, offset);
                instruments.enableInstrument(enableInstrumentCommand, timestamp, session);
                break;
            case Instrument.EIDER_ID:
                instruments.loadFromSnapshot(buffer, offset);
                break;
            default:
                log.warn("Unknown instrument command {}", eiderId);
        }
    }

    public void setSession(ClientSession session)
    {
        this.session = session;
    }

    public void setClusterTime(long timestamp)
    {
        this.timestamp = timestamp;
    }
}
