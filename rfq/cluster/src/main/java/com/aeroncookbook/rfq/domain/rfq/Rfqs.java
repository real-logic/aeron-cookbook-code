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

package com.aeroncookbook.rfq.domain.rfq;

import com.aeroncookbook.cluster.rfq.sbe.AcceptRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CounterRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.QuoteRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.RejectRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.Side;
import com.aeroncookbook.rfq.domain.instrument.Instruments;
import com.aeroncookbook.rfq.domain.rfq.states.RfqStates;
import com.aeroncookbook.rfq.domain.users.Users;
import com.aeroncookbook.rfq.infra.ClusterClientResponder;
import com.aeroncookbook.rfq.infra.SessionMessageContextImpl;
import com.aeroncookbook.rfq.infra.TimerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Rfqs
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Rfqs.class);
    private final SessionMessageContextImpl context;
    private final Instruments instruments;
    private final Users users;
    private final ClusterClientResponder clusterClientResponder;
    private final TimerManager timerManager;
    private final List<Rfq> rfqs = new ArrayList<>();
    private int rfqId = 0;

    public Rfqs(
        final SessionMessageContextImpl context,
        final Instruments instruments,
        final Users users,
        final ClusterClientResponder clusterClientResponder,
        final TimerManager timerManager)
    {
        this.context = context;
        this.instruments = instruments;
        this.users = users;
        this.clusterClientResponder = clusterClientResponder;
        this.timerManager = timerManager;
    }

    /**
     * Create a new RFQ.
     *
     * @param correlation the correlation id
     * @param expireTimeMs the time at which the RFQ expires
     * @param quantity the quantity of the RFQ
     * @param side the side of the RFQ
     * @param cusip the cusip of the instrument
     * @param userId the user id of the user creating the RFQ
     */
    public void createRfq(
        final String correlation,
        final long expireTimeMs,
        final long quantity,
        final Side side,
        final String cusip,
        final int userId)
    {
        if (!users.isValidUser(userId))
        {
            LOGGER.info("Cannot create RFQ: Invalid user id {} for RFQ", userId);
            clusterClientResponder.createRfqConfirm(correlation, null, CreateRfqResult.UNKNOWN_USER);
            return;
        }

        if (!instruments.isValidCusip(cusip))
        {
            LOGGER.info("Cannot create RFQ: Invalid cusip {} for RFQ", cusip);
            clusterClientResponder.createRfqConfirm(correlation, null, CreateRfqResult.UNKNOWN_CUSIP);
            return;
        }

        if (expireTimeMs <= context.getClusterTime())
        {
            LOGGER.info("Cannot create RFQ: RFQ expires in the past");
            clusterClientResponder.createRfqConfirm(correlation, null, CreateRfqResult.RFQ_EXPIRES_IN_PAST);
            return;
        }

        if (!instruments.isInstrumentEnabled(cusip))
        {
            LOGGER.info("Cannot create RFQ: Instrument {} is not enabled", cusip);
            clusterClientResponder.createRfqConfirm(correlation, null, CreateRfqResult.INSTRUMENT_NOT_ENABLED);
            return;
        }

        if (quantity < instruments.getMinSize(cusip))
        {
            LOGGER.info("Cannot create RFQ: Instrument {} min size not met", cusip);
            clusterClientResponder.createRfqConfirm(correlation, null, CreateRfqResult.INSTRUMENT_MIN_SIZE_NOT_MET);
            return;
        }

        final Rfq rfq = new Rfq(++rfqId, correlation, expireTimeMs, quantity, side, cusip, userId);
        rfqs.add(rfq);
        LOGGER.info("Created RFQ {}", rfq);

        //send a confirmation to the client that created the RFQ
        clusterClientResponder.createRfqConfirm(correlation, rfq, CreateRfqResult.SUCCESS);

        //broadcast the new RFQ to all clients
        clusterClientResponder.broadcastNewRfq(rfq);

        //schedule the RFQ to expire
        timerManager.scheduleTimer(rfq.getExpireTimeMs(), () -> expireRfq(rfq.getRfqId()));
    }

    private void expireRfq(final int rfqId)
    {
        final Rfq rfq = rfqs.stream().filter(r -> r.getRfqId() == rfqId).findFirst().orElse(null);
        if (rfq == null)
        {
            LOGGER.info("Cannot expire RFQ: RFQ {} not found", rfqId);
            return;
        }

        if (!rfq.canExpire())
        {
            LOGGER.info("Cannot expire RFQ: RFQ {}", rfqId);
            return;
        }

        rfq.expire();
        LOGGER.info("Expired RFQ {}", rfq);
        clusterClientResponder.broadcastRfqExpired(rfq);
    }

    /**
     * Cancel an RFQ.
     *
     * @param correlation the correlation id
     * @param rfqId the id of the RFQ to cancel
     * @param cancelUserId the user id of the user cancelling the RFQ
     */
    public void cancelRfq(final String correlation, final int rfqId, final int cancelUserId)
    {
        final Rfq rfq = rfqs.stream().filter(r -> r.getRfqId() == rfqId).findFirst().orElse(null);
        if (rfq == null)
        {
            LOGGER.info("Cannot cancel RFQ: RFQ {} not found", rfqId);
            clusterClientResponder.cancelRfqConfirm(correlation, null, CancelRfqResult.UNKNOWN_RFQ);
            return;
        }

        if (!rfq.canCancel())
        {
            LOGGER.info("Cannot cancel RFQ: RFQ {} invalid transition", rfqId);
            clusterClientResponder.cancelRfqConfirm(correlation, null, CancelRfqResult.INVALID_TRANSITION);
            return;
        }

        if (rfq.getRequesterUserId() != cancelUserId)
        {
            LOGGER.info("Cannot cancel RFQ: RFQ {} not created by user {}", rfqId, cancelUserId);
            clusterClientResponder.cancelRfqConfirm(correlation, null,
                CancelRfqResult.CANNOT_CANCEL_USER_NOT_REQUESTER);
            return;
        }

        rfq.cancel();
        LOGGER.info("Cancelled RFQ {}", rfq);
        clusterClientResponder.cancelRfqConfirm(correlation, rfq, CancelRfqResult.SUCCESS);
        clusterClientResponder.broadcastRfqCanceled(rfq);
    }

    public void quoteRfq(final String correlation, final int rfqId, final int responderUserId, final long price)
    {
        if (!users.isValidUser(responderUserId))
        {
            LOGGER.info("Cannot quote RFQ: Invalid user id {} for RFQ", responderUserId);
            clusterClientResponder.quoteRfqConfirm(correlation, null, QuoteRfqResult.UNKNOWN_USER);
            return;
        }

        final Rfq rfq = rfqs.stream().filter(r -> r.getRfqId() == rfqId).findFirst().orElse(null);
        if (rfq == null)
        {
            LOGGER.info("Cannot cancel RFQ: RFQ {} not found", rfqId);
            clusterClientResponder.quoteRfqConfirm(correlation, null, QuoteRfqResult.UNKNOWN_RFQ);
            return;
        }

        if (rfq.hasResponder())
        {
            LOGGER.info("Cannot quote RFQ: RFQ {} already has a responder", rfqId);
            clusterClientResponder.quoteRfqConfirm(correlation, null, QuoteRfqResult.ANOTHER_USER_RESPONDED);
            return;
        }

        if (rfq.getRequesterUserId() == responderUserId)
        {
            LOGGER.info("Cannot quote RFQ: RFQ {} cannot quote own RFQ", rfqId);
            clusterClientResponder.quoteRfqConfirm(correlation, null, QuoteRfqResult.CANNOT_QUOTE_OWN_RFQ);
            return;
        }

        if (!rfq.canQuote())
        {
            LOGGER.info("Cannot quote RFQ: RFQ {} invalid transition", rfqId);
            clusterClientResponder.quoteRfqConfirm(correlation, null, QuoteRfqResult.INVALID_TRANSITION);
            return;
        }

        rfq.quote(responderUserId, price);
        LOGGER.info("Quoted RFQ {}", rfq);
        clusterClientResponder.quoteRfqConfirm(correlation, rfq, QuoteRfqResult.SUCCESS);
        clusterClientResponder.broadcastRfqQuoted(rfq);
    }

    public void counterRfq(final String correlation, final int rfqId, final int counterUserId, final long price)
    {

        if (!users.isValidUser(counterUserId))
        {
            LOGGER.info("Cannot counter RFQ: Invalid user id {} for RFQ", counterUserId);
            clusterClientResponder.counterRfqConfirm(correlation, null, CounterRfqResult.UNKNOWN_USER);
            return;
        }

        final Rfq rfq = rfqs.stream().filter(r -> r.getRfqId() == rfqId).findFirst().orElse(null);
        if (rfq == null)
        {
            LOGGER.info("Cannot counter RFQ: RFQ {} not found", rfqId);
            clusterClientResponder.counterRfqConfirm(correlation, null, CounterRfqResult.UNKNOWN_RFQ);
            return;
        }

        if (!rfq.canCounter())
        {
            LOGGER.info("Cannot counter RFQ: RFQ {} invalid transition", rfqId);
            clusterClientResponder.counterRfqConfirm(correlation, null, CounterRfqResult.INVALID_TRANSITION);
            return;
        }

        if (rfq.getRequesterUserId() != counterUserId && rfq.getResponderUserId() != counterUserId)
        {
            LOGGER.info("Cannot counter RFQ: not involved with RFQ {}", rfqId);
            clusterClientResponder.counterRfqConfirm(correlation, null,
                CounterRfqResult.CANNOT_COUNTER_RFQ_NOT_INVOLVED_WITH);
            return;
        }

        if (rfq.getLastCounterUser() == Long.MIN_VALUE && counterUserId != rfq.getRequesterUserId())
        {
            LOGGER.info("Cannot counter RFQ: RFQ {} cannot counter first quote", rfqId);
            clusterClientResponder.counterRfqConfirm(correlation, null, CounterRfqResult.CANNOT_COUNTER_OWN_PRICE);
            return;
        }

        if (rfq.getLastCounterUser() != counterUserId && rfq.getCurrentState().getCurrentState() == RfqStates.COUNTERED)
        {
            LOGGER.info("Cannot counter RFQ: RFQ {} cannot counter own quote", rfqId);
            clusterClientResponder.counterRfqConfirm(correlation, null, CounterRfqResult.CANNOT_COUNTER_OWN_PRICE);
            return;
        }

        rfq.counter(counterUserId, price);
        LOGGER.info("Countered RFQ {}", rfq);
        clusterClientResponder.counterRfqConfirm(correlation, rfq, CounterRfqResult.SUCCESS);
        clusterClientResponder.broadcastRfqCountered(rfq);
    }

    public void acceptRfq(final String correlation, final int rfqId, final int acceptUserId)
    {
        if (!users.isValidUser(acceptUserId))
        {
            LOGGER.info("Cannot accept RFQ: Invalid user id {} for RFQ", acceptUserId);
            clusterClientResponder.acceptRfqConfirm(correlation, null, AcceptRfqResult.UNKNOWN_USER);
            return;
        }

        final Rfq rfq = rfqs.stream().filter(r -> r.getRfqId() == rfqId).findFirst().orElse(null);
        if (rfq == null)
        {
            LOGGER.info("Cannot accept RFQ: RFQ {} not found", rfqId);
            clusterClientResponder.acceptRfqConfirm(correlation, null, AcceptRfqResult.UNKNOWN_RFQ);
            return;
        }

        if (!rfq.canAccept())
        {
            LOGGER.info("Cannot accept RFQ: RFQ {} invalid transition", rfqId);
            clusterClientResponder.acceptRfqConfirm(correlation, null, AcceptRfqResult.INVALID_TRANSITION);
            return;
        }

        if (rfq.getRequesterUserId() != acceptUserId && rfq.getResponderUserId() != acceptUserId)
        {
            LOGGER.info("Cannot accept RFQ: not involved with RFQ {}", rfqId);
            clusterClientResponder.acceptRfqConfirm(correlation, null,
                AcceptRfqResult.CANNOT_ACCEPT_RFQ_NOT_INVOLVED_WITH);
            return;
        }

        if (rfq.getLastCounterUser() == Long.MIN_VALUE && acceptUserId != rfq.getRequesterUserId())
        {
            LOGGER.info("Cannot accept RFQ: RFQ {} cannot accept first quote", rfqId);
            clusterClientResponder.acceptRfqConfirm(correlation, null, AcceptRfqResult.CANNOT_ACCEPT_OWN_PRICE);
            return;
        }

        rfq.accept(acceptUserId);
        LOGGER.info("Accepted RFQ {}", rfq);
        clusterClientResponder.acceptRfqConfirm(correlation, rfq, AcceptRfqResult.SUCCESS);
        clusterClientResponder.broadcastRfqAccepted(rfq);
    }

    public void rejectRfq(final String correlation, final int rfqId, final int rejectUserId)
    {
        if (!users.isValidUser(rejectUserId))
        {
            LOGGER.info("Cannot reject RFQ: Invalid user id {} for RFQ", rejectUserId);
            clusterClientResponder.rejectRfqConfirm(correlation, null, RejectRfqResult.UNKNOWN_USER);
            return;
        }

        final Rfq rfq = rfqs.stream().filter(r -> r.getRfqId() == rfqId).findFirst().orElse(null);
        if (rfq == null)
        {
            LOGGER.info("Cannot reject RFQ: RFQ {} not found", rfqId);
            clusterClientResponder.rejectRfqConfirm(correlation, null, RejectRfqResult.UNKNOWN_RFQ);
            return;
        }

        if (!rfq.canReject())
        {
            LOGGER.info("Cannot reject RFQ: RFQ {} invalid transition", rfqId);
            clusterClientResponder.rejectRfqConfirm(correlation, null, RejectRfqResult.INVALID_TRANSITION);
            return;
        }

        if (rfq.getRequesterUserId() != rejectUserId && rfq.getResponderUserId() != rejectUserId)
        {
            LOGGER.info("Cannot reject RFQ: not involved with RFQ {}", rfqId);
            clusterClientResponder.rejectRfqConfirm(correlation, null,
                RejectRfqResult.CANNOT_REJECT_RFQ_NOT_INVOLVED_WITH);
            return;
        }

        if (rfq.getLastCounterUser() == Long.MIN_VALUE && rejectUserId != rfq.getRequesterUserId())
        {
            LOGGER.info("Cannot reject RFQ: RFQ {} cannot reject first quote", rfqId);
            clusterClientResponder.rejectRfqConfirm(correlation, null, RejectRfqResult.CANNOT_REJECT_OWN_PRICE);
            return;
        }

        if (rfq.getLastCounterUser() != rejectUserId && rfq.getCurrentState().getCurrentState() == RfqStates.COUNTERED)
        {
            LOGGER.info("Cannot reject RFQ: RFQ {} cannot reject own quote", rfqId);
            clusterClientResponder.rejectRfqConfirm(correlation, null, RejectRfqResult.CANNOT_REJECT_OWN_PRICE);
        }

        rfq.reject(rejectUserId);
        LOGGER.info("Rejected RFQ {}", rfq);
        clusterClientResponder.rejectRfqConfirm(correlation, rfq, RejectRfqResult.SUCCESS);
        clusterClientResponder.broadcastRfqRejected(rfq);
    }
}
