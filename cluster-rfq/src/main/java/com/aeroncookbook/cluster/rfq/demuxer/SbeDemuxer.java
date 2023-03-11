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

package com.aeroncookbook.cluster.rfq.demuxer;

import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentDecoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordEncoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.rfq.sbe.SetInstrumentEnabledFlagDecoder;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SbeDemuxer implements FragmentHandler
{
    private final Rfqs rfqs;
    private final Instruments instruments;
    final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final AddInstrumentDecoder instrumentCommand;
    private final SetInstrumentEnabledFlagDecoder enableInstrumentCommand;

    private final Logger log = LoggerFactory.getLogger(SbeDemuxer.class);
    private ClientSession session;
    private long timestamp;

    /**
     * Demuxer for RFQs and Instruments
     * @param rfqs
     * @param instruments
     */
    public SbeDemuxer(final Rfqs rfqs, final Instruments instruments)
    {
        this.rfqs = rfqs;
        this.instruments = instruments;
        this.instrumentCommand = new AddInstrumentDecoder();
        this.enableInstrumentCommand = new SetInstrumentEnabledFlagDecoder();
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        final int templateId = messageHeaderDecoder.templateId();

        switch (templateId)
        {
            //rfqs
            //instruments
            case AddInstrumentDecoder.TEMPLATE_ID ->
            {
                instrumentCommand.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                instruments.addInstrument(instrumentCommand.securityId(), instrumentCommand.cusip(),
                    instrumentCommand.enabled().equals(BooleanType.TRUE), instrumentCommand.minSize());
            }
            case SetInstrumentEnabledFlagDecoder.TEMPLATE_ID ->
            {
                enableInstrumentCommand.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                instruments.setEnabledFlagForCusip(enableInstrumentCommand.cusip(),
                    enableInstrumentCommand.enabled().equals(BooleanType.TRUE));
            }
            case InstrumentRecordEncoder.TEMPLATE_ID -> instruments.loadFromSnapshot(buffer, offset);
            default -> log.warn("Unknown instrument command {}", templateId);
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
