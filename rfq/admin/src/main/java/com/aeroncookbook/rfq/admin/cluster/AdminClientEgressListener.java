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

package com.aeroncookbook.rfq.admin.cluster;

import com.aeroncookbook.cluster.rfq.sbe.AcceptRfqConfirmEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.AcceptRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentResultDecoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqConfirmEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CounterRfqConfirmEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.CounterRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqConfirmEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentsListDecoder;
import com.aeroncookbook.cluster.rfq.sbe.ListInstrumentsResultDecoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.rfq.sbe.QuoteRfqConfirmEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.QuoteRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.RejectRfqConfirmEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RejectRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.RequestResult;
import com.aeroncookbook.cluster.rfq.sbe.RfqAcceptedEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqCanceledEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqCounteredEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqCreatedEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqExpiredEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqQuotedEventDecoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqRejectedEventDecoder;
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
    private final RfqCanceledEventDecoder rfqCanceledEventDecoder = new RfqCanceledEventDecoder();
    private final AddInstrumentResultDecoder addInstrumentResultDecoder = new AddInstrumentResultDecoder();
    private final SetInstrumentEnabledFlagResultDecoder setInstrumentEnabledFlagResultDecoder =
        new SetInstrumentEnabledFlagResultDecoder();
    private final CreateRfqConfirmEventDecoder createRfqConfirmEventDecoder = new CreateRfqConfirmEventDecoder();
    private final ListInstrumentsResultDecoder listInstrumentsResultDecoder = new ListInstrumentsResultDecoder();
    private final RfqCreatedEventDecoder rfqCreatedEventDecoder = new RfqCreatedEventDecoder();
    private final InstrumentsListDecoder instrumentsListDecoder = new InstrumentsListDecoder();
    private final RfqExpiredEventDecoder rfqExpiredEventDecoder = new RfqExpiredEventDecoder();
    private final CancelRfqConfirmEventDecoder cancelRfqConfirmEventDecoder = new CancelRfqConfirmEventDecoder();
    private final QuoteRfqConfirmEventDecoder quoteRfqConfirmEventDecoder = new QuoteRfqConfirmEventDecoder();
    private final RfqQuotedEventDecoder rfqQuotedEventDecoder = new RfqQuotedEventDecoder();
    private final CounterRfqConfirmEventDecoder counterRfqConfirmEventDecoder = new CounterRfqConfirmEventDecoder();
    private final RfqCounteredEventDecoder rfqCounteredEventDecoder = new RfqCounteredEventDecoder();
    private final AcceptRfqConfirmEventDecoder acceptRfqConfirmEventDecoder = new AcceptRfqConfirmEventDecoder();
    private final RejectRfqConfirmEventDecoder rejectRfqConfirmEventDecoder = new RejectRfqConfirmEventDecoder();
    private final RfqAcceptedEventDecoder rfqAcceptedEventDecoder = new RfqAcceptedEventDecoder();
    private final RfqRejectedEventDecoder rfqRejectedEventDecoder = new RfqRejectedEventDecoder();

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
            case RfqExpiredEventDecoder.TEMPLATE_ID -> rfqExpiredEvent(buffer, offset);
            case CancelRfqConfirmEventDecoder.TEMPLATE_ID -> cancelRfqResult(buffer, offset);
            case RfqCanceledEventDecoder.TEMPLATE_ID -> rfqCanceledEvent(buffer, offset);
            case QuoteRfqConfirmEventDecoder.TEMPLATE_ID -> quotedRfqConfirmEvent(buffer, offset);
            case RfqQuotedEventDecoder.TEMPLATE_ID -> rfqQuotedEvent(buffer, offset);
            case AddInstrumentResultDecoder.TEMPLATE_ID -> addInstrumentResult(buffer, offset);
            case SetInstrumentEnabledFlagResultDecoder.TEMPLATE_ID -> setInstrumentEnabledFlag(buffer, offset);
            case ListInstrumentsResultDecoder.TEMPLATE_ID -> listInstruments(buffer, offset);
            case InstrumentsListDecoder.TEMPLATE_ID -> displayInstruments(buffer, offset);
            case CounterRfqConfirmEventDecoder.TEMPLATE_ID -> counterRfqConfirmEvent(buffer, offset);
            case RfqCounteredEventDecoder.TEMPLATE_ID -> rfqCounteredEvent(buffer, offset);
            case AcceptRfqConfirmEventDecoder.TEMPLATE_ID -> acceptRfqConfirmEvent(buffer, offset);
            case RfqAcceptedEventDecoder.TEMPLATE_ID -> rfqAcceptedEvent(buffer, offset);
            case RejectRfqConfirmEventDecoder.TEMPLATE_ID -> rejectRfqConfirmEvent(buffer, offset);
            case RfqRejectedEventDecoder.TEMPLATE_ID -> rfqRejectedEvent(buffer, offset);
            default -> log("unknown message type: " + messageHeaderDecoder.templateId(), AttributedStyle.RED);
        }
    }

    private void rfqRejectedEvent(final DirectBuffer buffer, final int offset)
    {
        rfqRejectedEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final int rfqId = rfqRejectedEventDecoder.rfqId();
        log("RFQ rejected: id=" + rfqId, AttributedStyle.RED);
    }

    private void rejectRfqConfirmEvent(final DirectBuffer buffer, final int offset)
    {
        rejectRfqConfirmEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = rejectRfqConfirmEventDecoder.correlation();
        final int rfqId = rejectRfqConfirmEventDecoder.rfqId();
        final RejectRfqResult result = rejectRfqConfirmEventDecoder.result();
        if (result == RejectRfqResult.SUCCESS)
        {
            log("RFQ rejected: id=" + rfqId, AttributedStyle.YELLOW);
        }
        else
        {
            log("RFQ reject failed: id=" + rfqId, AttributedStyle.RED);
        }
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void rfqAcceptedEvent(final DirectBuffer buffer, final int offset)
    {
        rfqAcceptedEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final int rfqId = rfqAcceptedEventDecoder.rfqId();
        log("RFQ accepted: id=" + rfqId, AttributedStyle.CYAN);
    }

    private void acceptRfqConfirmEvent(final DirectBuffer buffer, final int offset)
    {
        acceptRfqConfirmEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = acceptRfqConfirmEventDecoder.correlation();
        final int rfqId = acceptRfqConfirmEventDecoder.rfqId();
        final AcceptRfqResult result = acceptRfqConfirmEventDecoder.result();
        if (result == AcceptRfqResult.SUCCESS)
        {
            log("RFQ accepted: id=" + rfqId, AttributedStyle.GREEN);
        }
        else
        {
            log("RFQ accept failed: id=" + rfqId + " reason=" + result, AttributedStyle.RED);
        }
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void rfqCounteredEvent(final DirectBuffer buffer, final int offset)
    {
        rfqCounteredEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final int rfqId = rfqCounteredEventDecoder.rfqId();
        final long price = rfqCounteredEventDecoder.price();
        log("RFQ countered: id=" + rfqId + " price=" + price, AttributedStyle.GREEN);
    }

    private void counterRfqConfirmEvent(final DirectBuffer buffer, final int offset)
    {
        counterRfqConfirmEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = counterRfqConfirmEventDecoder.correlation();
        final int rfqId = counterRfqConfirmEventDecoder.rfqId();
        final CounterRfqResult result = counterRfqConfirmEventDecoder.result();
        if (result == CounterRfqResult.SUCCESS)
        {
            log("RFQ countered: id=" + rfqId, AttributedStyle.GREEN);
        }
        else
        {
            log("RFQ countered: id=" + rfqId + " failed: " + result, AttributedStyle.RED);
        }
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void rfqQuotedEvent(final DirectBuffer buffer, final int offset)
    {
        rfqQuotedEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = rfqQuotedEventDecoder.correlation();
        final int rfqId = rfqQuotedEventDecoder.rfqId();
        final long price = rfqQuotedEventDecoder.price();
        log("RFQ Quoted: id=" + rfqId + " price=" + price, AttributedStyle.GREEN);
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void quotedRfqConfirmEvent(final DirectBuffer buffer, final int offset)
    {
        quoteRfqConfirmEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = quoteRfqConfirmEventDecoder.correlation();
        final int rfqId = quoteRfqConfirmEventDecoder.rfqId();
        final QuoteRfqResult result = quoteRfqConfirmEventDecoder.result();
        if (result != QuoteRfqResult.SUCCESS)
        {
            log("Quote RFQ failed: id=" + rfqId + " result=" + result, AttributedStyle.RED);
        }
        else
        {
            log("Quote RFQ succeeded: id=" + rfqId, AttributedStyle.GREEN);
        }
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void rfqCanceledEvent(final DirectBuffer buffer, final int offset)
    {
        rfqCanceledEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final int rfqId = rfqCanceledEventDecoder.rfqId();
        log("RFQ canceled: id=" + rfqId, AttributedStyle.RED);
    }

    private void cancelRfqResult(final DirectBuffer buffer, final int offset)
    {
        cancelRfqConfirmEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final String correlation = cancelRfqConfirmEventDecoder.correlation();
        final int rfqId = cancelRfqConfirmEventDecoder.rfqId();
        final CancelRfqResult result = cancelRfqConfirmEventDecoder.result();
        if (result != CancelRfqResult.SUCCESS)
        {
            log("Cancel RFQ failed: id=" + rfqId + " result=" + result, AttributedStyle.RED);
        }
        else
        {
            log("Cancel RFQ succeeded: id=" + rfqId, AttributedStyle.GREEN);
        }
        pendingMessageManager.markMessageAsReceived(correlation);
    }

    private void rfqExpiredEvent(final DirectBuffer buffer, final int offset)
    {
        rfqExpiredEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final int rfqId = rfqExpiredEventDecoder.rfqId();
        log("RFQ expired: id=" + rfqId, AttributedStyle.RED);
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
