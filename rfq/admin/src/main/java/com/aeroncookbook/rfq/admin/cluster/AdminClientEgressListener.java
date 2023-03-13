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

package com.aeroncookbook.rfq.admin.cluster;

import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentResultDecoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqConfirmEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentsListDecoder;
import com.aeroncookbook.cluster.rfq.sbe.ListInstrumentsResultDecoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RequestResult;
import com.aeroncookbook.cluster.rfq.sbe.RfqCreatedEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.SetInstrumentEnabledFlagResultDecoder;
import com.aeroncookbook.cluster.rfq.sbe.Side;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.logbuffer.Header;

import org.agrona.DirectBuffer;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin client egress listener
 */
public class AdminClientEgressListener implements EgressListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminClientEgressListener.class);
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    private final AddInstrumentResultDecoder addInstrumentResultDecoder = new AddInstrumentResultDecoder();
    private final SetInstrumentEnabledFlagResultDecoder setInstrumentEnabledFlagResultDecoder =
        new SetInstrumentEnabledFlagResultDecoder();
    private final CreateRfqConfirmEventDecoder createRfqConfirmEventDecoder = new CreateRfqConfirmEventDecoder();
    private final ListInstrumentsResultDecoder listInstrumentsResultDecoder = new ListInstrumentsResultDecoder();
    private final RfqCreatedEventDecoder rfqCreatedEventDecoder = new RfqCreatedEventDecoder();
    private final InstrumentsListDecoder instrumentsListDecoder = new InstrumentsListDecoder();

    private final PendingMessageManager pendingMessageManager;

    private LineReader lineReader;

    /**
     * Constructor
     * @param pendingMessageManager the manager for pending messages
     */
    public AdminClientEgressListener(final PendingMessageManager pendingMessageManager)
    {
        this.pendingMessageManager = pendingMessageManager;
    }

    @Override
    public void onMessage(
        final long clusterSessionId,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        if (length < MessageHeaderDecoder.ENCODED_LENGTH)
        {
            LOGGER.warn("Message too short");
            return;
        }
        messageHeaderDecoder.wrap(buffer, offset);

        switch (messageHeaderDecoder.templateId())
        {
            case CreateRfqConfirmEventDecoder.TEMPLATE_ID -> createRfqConfirmEvent(buffer, offset);
            case RfqCreatedEventDecoder.TEMPLATE_ID -> rfqCreatedEvent(buffer, offset);
            case AddInstrumentResultDecoder.TEMPLATE_ID -> addInstrumentResult(buffer, offset);
            case SetInstrumentEnabledFlagResultDecoder.TEMPLATE_ID -> setInstrumentEnabledFlag(buffer, offset);
            case ListInstrumentsResultDecoder.TEMPLATE_ID -> listInstruments(buffer, offset);
            case InstrumentsListDecoder.TEMPLATE_ID -> displayInstruments(buffer, offset);
            default -> log("unknown message type: " + messageHeaderDecoder.templateId(), AttributedStyle.RED);
        }
    }

    private void rfqCreatedEvent(final DirectBuffer buffer, final int offset)
    {
        rfqCreatedEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String cusip = rfqCreatedEventDecoder.cusip();
        final long expireTimeMs = rfqCreatedEventDecoder.expireTimeMs();
        final long quantity = rfqCreatedEventDecoder.quantity();
        final Side side = rfqCreatedEventDecoder.requesterSide();
        final int rfqId = rfqCreatedEventDecoder.rfqId();

        log("RFQ created: id=" + rfqId + " cusip='" + cusip + "' qty=" + quantity + " side=" +
            side + " expires=" + expireTimeMs, AttributedStyle.GREEN);
    }

    private void createRfqConfirmEvent(final DirectBuffer buffer, final int offset)
    {
        createRfqConfirmEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = createRfqConfirmEventDecoder.correlation();
        final int rfqId = createRfqConfirmEventDecoder.rfqId();
        final CreateRfqResult result = createRfqConfirmEventDecoder.result();

        if (result != CreateRfqResult.SUCCESS)
        {
            log("Create RFQ result: " + result.name(), AttributedStyle.RED);
        }
        else
        {
            log("Created RFQ with ID: " + rfqId, AttributedStyle.GREEN);
        }

        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void listInstruments(final DirectBuffer buffer, final int offset)
    {
        listInstrumentsResultDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = listInstrumentsResultDecoder.correlation();
        final RequestResult result = listInstrumentsResultDecoder.result();
        log("List instruments result: " + result.name(), AttributedStyle.GREEN);
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void setInstrumentEnabledFlag(final DirectBuffer buffer, final int offset)
    {
        setInstrumentEnabledFlagResultDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = setInstrumentEnabledFlagResultDecoder.correlation();
        final RequestResult result = setInstrumentEnabledFlagResultDecoder.result();
        log("Set instrument enabled flag result: " + result.name(), AttributedStyle.GREEN);
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void addInstrumentResult(final DirectBuffer buffer, final int offset)
    {
        addInstrumentResultDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = addInstrumentResultDecoder.correlation();
        final RequestResult result = addInstrumentResultDecoder.result();
        log("Add instrument result: " + result.name(), AttributedStyle.GREEN);
        pendingMessageManager.markMessageAsReceived(correlation);
    }


    private void displayInstruments(final DirectBuffer buffer, final int offset)
    {
        instrumentsListDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        pendingMessageManager.markMessageAsReceived(instrumentsListDecoder.correlation());
        final InstrumentsListDecoder.ValuesDecoder values = instrumentsListDecoder.values();
        final int count = values.count();
        if (0 == count)
        {
            log("No instruments exist in the cluster.",
                AttributedStyle.YELLOW);
        }
        else
        {
            log("Instrument count: " + count, AttributedStyle.YELLOW);
            while (values.hasNext())
            {
                values.next();
                final String participantId = values.cusip();
                final long minSize = values.minSize();
                final boolean enabled = values.enabled() == BooleanType.TRUE;
                log("Instrument: " + participantId + " minSize: " + minSize + " enabled: " + enabled,
                    AttributedStyle.GREEN);
            }
        }
    }

    @Override
    public void onSessionEvent(
        final long correlationId,
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final EventCode code,
        final String detail)
    {
        if (code != EventCode.OK)
        {
            log("Session event: " + code.name() + " " + detail + ". leadershipTermId=" + leadershipTermId,
                AttributedStyle.YELLOW);
        }
    }

    @Override
    public void onNewLeader(
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final String ingressEndpoints)
    {
        log("New Leader: " + leaderMemberId + ". leadershipTermId=" + leadershipTermId, AttributedStyle.YELLOW);
    }

    /**
     * Sets the terminal
     *
     * @param lineReader the lineReader
     */
    public void setLineReader(final LineReader lineReader)
    {
        this.lineReader = lineReader;
    }

    /**
     * Logs a message to the terminal if available or to the logger if not
     *
     * @param message message to log
     * @param color   message color to use
     */
    private void log(final String message, final int color)
    {
        LineReaderHelper.log(lineReader, message, color);
    }
}
