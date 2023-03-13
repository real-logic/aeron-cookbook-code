package com.aeroncookbook.rfq.domain.rfq;

import com.aeroncookbook.cluster.rfq.sbe.CreateRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.Side;
import com.aeroncookbook.rfq.domain.instrument.Instruments;
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
}
