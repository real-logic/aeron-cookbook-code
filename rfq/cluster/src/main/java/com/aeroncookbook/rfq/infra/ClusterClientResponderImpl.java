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

import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentResultEncoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqConfirmEventEncoder;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqConfirmEventEncoder;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentsListEncoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderEncoder;
import com.aeroncookbook.cluster.rfq.sbe.QuoteRfqConfirmEventEncoder;
import com.aeroncookbook.cluster.rfq.sbe.QuoteRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.RequestResult;
import com.aeroncookbook.cluster.rfq.sbe.RfqCanceledEventEncoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqCreatedEventEncoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqExpiredEventEncoder;
import com.aeroncookbook.cluster.rfq.sbe.RfqQuotedEventEncoder;
import com.aeroncookbook.cluster.rfq.sbe.SetInstrumentEnabledFlagResultEncoder;
import com.aeroncookbook.rfq.domain.instrument.Instrument;
import com.aeroncookbook.rfq.domain.rfq.Rfq;
import org.agrona.ExpandableDirectByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementation of the {@link ClusterClientResponder} interface which returns SBE encoded results to the client
 */
public class ClusterClientResponderImpl implements ClusterClientResponder
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterClientResponderImpl.class);
    private final SessionMessageContextImpl context;
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(1024);
    private final RfqExpiredEventEncoder rfqExpiredEventEncoder = new RfqExpiredEventEncoder();
    private final RfqCanceledEventEncoder rfqCanceledEventEncoder = new RfqCanceledEventEncoder();
    private final AddInstrumentResultEncoder addInstrumentResultEncoder = new AddInstrumentResultEncoder();
    private final SetInstrumentEnabledFlagResultEncoder setInstrumentEnabledFlagResultEncoder =
        new SetInstrumentEnabledFlagResultEncoder();
    private final InstrumentsListEncoder instrumentsListEncoder = new InstrumentsListEncoder();
    private final CreateRfqConfirmEventEncoder createRfqConfirmEventEncoder = new CreateRfqConfirmEventEncoder();
    private final RfqCreatedEventEncoder rfqCreatedEventEncoder = new RfqCreatedEventEncoder();
    private final CancelRfqConfirmEventEncoder cancelRfqConfirmEventEncoder = new CancelRfqConfirmEventEncoder();
    private final QuoteRfqConfirmEventEncoder quoteRfqConfirmEventEncoder = new QuoteRfqConfirmEventEncoder();
    private final RfqQuotedEventEncoder rfqQuotedEventEncoder = new RfqQuotedEventEncoder();

    public ClusterClientResponderImpl(final SessionMessageContextImpl context)
    {
        this.context = context;
    }

    @Override
    public void sendInstrumentAdded(final String correlation)
    {
        addInstrumentResultEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        addInstrumentResultEncoder.correlation(correlation);
        addInstrumentResultEncoder.result(RequestResult.SUCCESS);
        context.reply(buffer, 0, messageHeaderEncoder.encodedLength() +
            addInstrumentResultEncoder.encodedLength());
    }

    @Override
    public void sendInstrumentEnabledFlagSet(final String correlation, final boolean success)
    {
        setInstrumentEnabledFlagResultEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        setInstrumentEnabledFlagResultEncoder.correlation(correlation);
        setInstrumentEnabledFlagResultEncoder.result(success ? RequestResult.SUCCESS : RequestResult.ERROR);
        context.reply(buffer, 0, messageHeaderEncoder.encodedLength() +
            setInstrumentEnabledFlagResultEncoder.encodedLength());
    }

    @Override
    public void sendInstruments(final String correlation, final List<Instrument> values)
    {
        instrumentsListEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        instrumentsListEncoder.correlation(correlation);
        final InstrumentsListEncoder.ValuesEncoder valuesEncoder = instrumentsListEncoder.valuesCount(values.size());

        for (final Instrument instrument : values)
        {
            valuesEncoder
                .next()
                .cusip(instrument.getCusip())
                .enabled(instrument.isEnabled() ? BooleanType.TRUE : BooleanType.FALSE)
                .minSize(instrument.getMinSize());
        }

        context.reply(buffer, 0, messageHeaderEncoder.encodedLength() +
            instrumentsListEncoder.encodedLength());
    }

    @Override
    public void broadcastNewRfq(final Rfq rfq)
    {
        rfqCreatedEventEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        rfqCreatedEventEncoder.cusip(rfq.getCusip());
        rfqCreatedEventEncoder.expireTimeMs(rfq.getExpireTimeMs());
        rfqCreatedEventEncoder.quantity(rfq.getQuantity());
        rfqCreatedEventEncoder.requesterSide(rfq.getRequesterSide());
        rfqCreatedEventEncoder.rfqId(rfq.getRfqId());

        context.broadcast(buffer, 0, messageHeaderEncoder.encodedLength() +
            rfqCreatedEventEncoder.encodedLength());
    }

    @Override
    public void createRfqConfirm(final String correlation, final Rfq rfq, final CreateRfqResult result)
    {
        createRfqConfirmEventEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        createRfqConfirmEventEncoder.correlation(correlation);
        if (rfq != null)
        {
            createRfqConfirmEventEncoder.rfqId(rfq.getRfqId());
        }
        else
        {
            createRfqConfirmEventEncoder.rfqId(-1);
        }
        createRfqConfirmEventEncoder.result(result);

        context.reply(buffer, 0, messageHeaderEncoder.encodedLength() +
            createRfqConfirmEventEncoder.encodedLength());
    }

    @Override
    public void broadcastRfqExpired(final Rfq rfq)
    {
        rfqExpiredEventEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        rfqExpiredEventEncoder.rfqId(rfq.getRfqId());
        rfqExpiredEventEncoder.requesterUserId(rfq.getRfqId());
        rfqExpiredEventEncoder.responderUserId(rfq.getRfqId());
        context.broadcast(buffer, 0, messageHeaderEncoder.encodedLength() +
            rfqExpiredEventEncoder.encodedLength());
    }

    @Override
    public void cancelRfqConfirm(final String correlation, final Rfq rfq, final CancelRfqResult result)
    {
        cancelRfqConfirmEventEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        cancelRfqConfirmEventEncoder.correlation(correlation);
        if (rfq != null)
        {
            cancelRfqConfirmEventEncoder.rfqId(rfq.getRfqId());
        }
        else
        {
            cancelRfqConfirmEventEncoder.rfqId(-1);
        }
        cancelRfqConfirmEventEncoder.result(result);
        context.reply(buffer, 0, messageHeaderEncoder.encodedLength() +
            cancelRfqConfirmEventEncoder.encodedLength());
    }

    @Override
    public void broadcastRfqCanceled(final Rfq rfq)
    {
        rfqCanceledEventEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        rfqCanceledEventEncoder.rfqId(rfq.getRfqId());
        rfqCanceledEventEncoder.requesterUserId(rfq.getRfqId());
        rfqCanceledEventEncoder.responderUserId(rfq.getRfqId());
        context.broadcast(buffer, 0, messageHeaderEncoder.encodedLength() +
            rfqCanceledEventEncoder.encodedLength());
    }

    @Override
    public void quoteRfqConfirm(final String correlation, final Rfq rfq, final QuoteRfqResult result)
    {
        quoteRfqConfirmEventEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        quoteRfqConfirmEventEncoder.correlation(correlation);
        if (rfq != null)
        {
            quoteRfqConfirmEventEncoder.rfqId(rfq.getRfqId());
        }
        else
        {
            quoteRfqConfirmEventEncoder.rfqId(-1);
        }
        quoteRfqConfirmEventEncoder.result(result);
        context.reply(buffer, 0, messageHeaderEncoder.encodedLength() +
            quoteRfqConfirmEventEncoder.encodedLength());
    }

    @Override
    public void broadcastRfqQuoted(final Rfq rfq)
    {
        rfqQuotedEventEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        rfqQuotedEventEncoder.rfqId(rfq.getRfqId());
        rfqQuotedEventEncoder.price(rfq.getPrice());
        rfqQuotedEventEncoder.requesterUserId(rfq.getRfqId());
        rfqQuotedEventEncoder.responderUserId(rfq.getRfqId());
        context.broadcast(buffer, 0, messageHeaderEncoder.encodedLength() +
            rfqQuotedEventEncoder.encodedLength());
    }
}
