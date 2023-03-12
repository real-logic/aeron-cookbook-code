/*
 * Copyright 2023 Adaptive Financial Consulting
 * Copyright 2023 Shaun Laurens
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

package com.aeroncookbook.rfq.infra;

import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentDecoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordEncoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.rfq.sbe.SetInstrumentEnabledFlagDecoder;
import com.aeroncookbook.rfq.domain.instrument.Instruments;
import com.aeroncookbook.rfq.domain.rfq.Rfqs;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demultiplexes messages from the ingress stream to the appropriate domain handler.
 */
public class SbeDemuxer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SbeDemuxer.class);
    private final Instruments instruments;
    private final Rfqs rfqs;
    private final ClusterClientResponder responder;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    private final AddInstrumentDecoder addInstrumentDecoder = new AddInstrumentDecoder();
    private final SetInstrumentEnabledFlagDecoder setInstrumentEnabledDecoder = new SetInstrumentEnabledFlagDecoder();


    /**
     * Dispatches ingress messages to domain logic.
     *
     * @param instruments the instrument domain model to which commands are dispatched
     * @param rfqs        the RFQ domain model to which commands are dispatched
     * @param responder   the responder to which responses are sent
     */
    public SbeDemuxer(
        final Instruments instruments,
        final Rfqs rfqs,
        final ClusterClientResponder responder)
    {
        this.instruments = instruments;
        this.rfqs = rfqs;
        this.responder = responder;
    }

    /**
     * Dispatch a message to the appropriate domain handler.
     *
     * @param buffer the buffer containing the inbound message, including a header
     * @param offset the offset to apply
     * @param length the length of the message
     */
    public void dispatch(final DirectBuffer buffer, final int offset, final int length)
    {
        if (length < MessageHeaderDecoder.ENCODED_LENGTH)
        {
            LOGGER.error("Message too short, ignored.");
            return;
        }
        headerDecoder.wrap(buffer, offset);

        switch (headerDecoder.templateId())
        {
            case AddInstrumentDecoder.TEMPLATE_ID -> addInstrument(buffer, offset);
            case SetInstrumentEnabledFlagDecoder.TEMPLATE_ID -> setInstrumentEnabledFlag(buffer, offset);
            case InstrumentRecordEncoder.TEMPLATE_ID -> instruments.loadFromSnapshot(buffer, offset);
            default -> LOGGER.error("Unknown message template {}, ignored.", headerDecoder.templateId());
        }
    }

    private void setInstrumentEnabledFlag(final DirectBuffer buffer, final int offset)
    {
        setInstrumentEnabledDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        instruments.setEnabledFlagForCusip(setInstrumentEnabledDecoder.cusip(),
            setInstrumentEnabledDecoder.enabled().equals(BooleanType.TRUE));
    }

    private void addInstrument(final DirectBuffer buffer, final int offset)
    {
        addInstrumentDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        instruments.addInstrument(addInstrumentDecoder.securityId(), addInstrumentDecoder.cusip(),
            addInstrumentDecoder.enabled().equals(BooleanType.TRUE), addInstrumentDecoder.minSize());
    }
}
