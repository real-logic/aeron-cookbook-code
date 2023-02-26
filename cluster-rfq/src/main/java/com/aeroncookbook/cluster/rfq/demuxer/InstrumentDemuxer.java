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

package com.aeroncookbook.cluster.rfq.demuxer;

import com.aeroncookbook.cluster.rfq.instrument.gen.EnableInstrumentCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentDecoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordEncoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstrumentDemuxer implements FragmentHandler
{
    private final Instruments instruments;
    final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final AddInstrumentDecoder instrumentCommand;
    private final EnableInstrumentCommand enableInstrumentCommand;
    private final Logger log = LoggerFactory.getLogger(InstrumentDemuxer.class);
    private ClientSession session;
    private long timestamp;

    public InstrumentDemuxer(final Instruments instruments)
    {
        this.instruments = instruments;
        this.instrumentCommand = new AddInstrumentDecoder();
        this.enableInstrumentCommand = new EnableInstrumentCommand();
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        final int templateId = messageHeaderDecoder.templateId();
        switch (templateId)
        {
            case AddInstrumentDecoder.TEMPLATE_ID:
                instrumentCommand.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                instruments.addInstrument(instrumentCommand.securityId(), instrumentCommand.cusip(),
                    instrumentCommand.enabled().equals(BooleanType.TRUE), instrumentCommand.minSize());
                break;
            case EnableInstrumentCommand.EIDER_ID:
                enableInstrumentCommand.setUnderlyingBuffer(buffer, offset);
                instruments.enableInstrument(enableInstrumentCommand, timestamp);
                break;
            case InstrumentRecordEncoder.TEMPLATE_ID:
                instruments.loadFromSnapshot(buffer, offset);
                break;
            default:
                log.warn("Unknown instrument command {}", templateId);
        }
    }

    public void setSession(final ClientSession session)
    {
        this.session = session;
    }

    public void setClusterTime(final long timestamp)
    {
        this.timestamp = timestamp;
    }
}
