/*
 * Copyright 2023 Adaptive Financial Consulting Ltd
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

import com.aeroncookbook.cluster.rfq.sbe.AcceptRfqCommandDecoder;
import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentDecoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqCommandDecoder;
import com.aeroncookbook.cluster.rfq.sbe.CounterRfqCommandDecoder;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqCommandDecoder;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordDecoder;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordEncoder;
import com.aeroncookbook.cluster.rfq.sbe.ListInstrumentsCommandDecoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.rfq.sbe.QuoteRfqCommandDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RejectRfqCommandDecoder;
import com.aeroncookbook.cluster.rfq.sbe.SetInstrumentEnabledFlagDecoder;
import com.aeroncookbook.rfq.domain.instrument.InstrumentAddType;
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
    private final InstrumentRecordDecoder instrumentRecordDecoder = new InstrumentRecordDecoder();
    private final ListInstrumentsCommandDecoder listInstrumentsCommandDecoder = new ListInstrumentsCommandDecoder();
    private final AddInstrumentDecoder addInstrumentDecoder = new AddInstrumentDecoder();
    private final CreateRfqCommandDecoder createRfqCommandDecoder = new CreateRfqCommandDecoder();
    private final SetInstrumentEnabledFlagDecoder setInstrumentEnabledDecoder = new SetInstrumentEnabledFlagDecoder();
    private final CancelRfqCommandDecoder cancelRfqCommandDecoder = new CancelRfqCommandDecoder();
    private final QuoteRfqCommandDecoder quoteRfqCommandDecoder = new QuoteRfqCommandDecoder();
    private final CounterRfqCommandDecoder counterRfqCommandDecoder = new CounterRfqCommandDecoder();
    private final AcceptRfqCommandDecoder acceptRfqCommandDecoder = new AcceptRfqCommandDecoder();
    private final RejectRfqCommandDecoder rejectRfqCommandDecoder = new RejectRfqCommandDecoder();

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
            case InstrumentRecordEncoder.TEMPLATE_ID -> initializeInstrument(buffer, offset);
            case ListInstrumentsCommandDecoder.TEMPLATE_ID -> listInstruments(buffer, offset);
            case CreateRfqCommandDecoder.TEMPLATE_ID -> createRfq(buffer, offset);
            case CancelRfqCommandDecoder.TEMPLATE_ID -> cancelRfq(buffer, offset);
            case QuoteRfqCommandDecoder.TEMPLATE_ID -> quoteRfq(buffer, offset);
            case CounterRfqCommandDecoder.TEMPLATE_ID -> counterRfq(buffer, offset);
            case AcceptRfqCommandDecoder.TEMPLATE_ID -> acceptRfq(buffer, offset);
            case RejectRfqCommandDecoder.TEMPLATE_ID -> rejectRfq(buffer, offset);
            default -> LOGGER.error("Unknown message template {}, ignored.", headerDecoder.templateId());
        }
    }

    private void rejectRfq(final DirectBuffer buffer, final int offset)
    {
        rejectRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        rfqs.rejectRfq(
            rejectRfqCommandDecoder.correlation(),
            rejectRfqCommandDecoder.rfqId(),
            rejectRfqCommandDecoder.responderUserId());
    }

    private void acceptRfq(final DirectBuffer buffer, final int offset)
    {
        acceptRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        rfqs.acceptRfq(
            acceptRfqCommandDecoder.correlation(),
            acceptRfqCommandDecoder.rfqId(),
            acceptRfqCommandDecoder.acceptUserId());
    }

    private void counterRfq(final DirectBuffer buffer, final int offset)
    {
        counterRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        rfqs.counterRfq(
            counterRfqCommandDecoder.correlation(),
            counterRfqCommandDecoder.rfqId(),
            counterRfqCommandDecoder.counterUserId(),
            counterRfqCommandDecoder.price());
    }

    private void quoteRfq(final DirectBuffer buffer, final int offset)
    {
        quoteRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        rfqs.quoteRfq(
            quoteRfqCommandDecoder.correlation(),
            quoteRfqCommandDecoder.rfqId(),
            quoteRfqCommandDecoder.responderUserId(),
            quoteRfqCommandDecoder.price());
    }

    private void cancelRfq(final DirectBuffer buffer, final int offset)
    {
        cancelRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        rfqs.cancelRfq(
            cancelRfqCommandDecoder.correlation(),
            cancelRfqCommandDecoder.rfqId(),
            cancelRfqCommandDecoder.cancelUserId());
    }

    private void createRfq(final DirectBuffer buffer, final int offset)
    {
        createRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        rfqs.createRfq(
            createRfqCommandDecoder.correlation(),
            createRfqCommandDecoder.expireTimeMs(),
            createRfqCommandDecoder.quantity(),
            createRfqCommandDecoder.requesterSide(),
            createRfqCommandDecoder.cusip(),
            createRfqCommandDecoder.requesterUserId());
    }

    private void listInstruments(final DirectBuffer buffer, final int offset)
    {
        listInstrumentsCommandDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        instruments.listInstruments(listInstrumentsCommandDecoder.correlation());
    }

    private void initializeInstrument(final DirectBuffer buffer, final int offset)
    {
        instrumentRecordDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

        instruments.addInstrument(
            InstrumentAddType.SNAPSHOT_LOAD,
            "",
            instrumentRecordDecoder.cusip(),
            instrumentRecordDecoder.enabled().equals(BooleanType.TRUE),
            instrumentRecordDecoder.minSize());
    }

    private void setInstrumentEnabledFlag(final DirectBuffer buffer, final int offset)
    {
        setInstrumentEnabledDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

        instruments.setEnabledFlagForCusip(
            setInstrumentEnabledDecoder.correlation(),
            setInstrumentEnabledDecoder.cusip(),
            setInstrumentEnabledDecoder.enabled().equals(BooleanType.TRUE));
    }

    private void addInstrument(final DirectBuffer buffer, final int offset)
    {
        addInstrumentDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

        instruments.addInstrument(
            InstrumentAddType.INTERACTIVE,
            addInstrumentDecoder.correlation(),
            addInstrumentDecoder.cusip(),
            addInstrumentDecoder.enabled().equals(BooleanType.TRUE),
            addInstrumentDecoder.minSize());
    }
}
