package com.aeroncookbook.rfq.domain.rfq;

import com.aeroncookbook.cluster.rfq.sbe.Side;
import com.aeroncookbook.rfq.domain.rfq.states.RfqCreated;
import com.aeroncookbook.rfq.domain.rfq.states.RfqState;
import com.aeroncookbook.rfq.domain.rfq.states.RfqStateHelper;
import com.aeroncookbook.rfq.domain.rfq.states.RfqStates;

public class Rfq
{
    private final String correlation;
    private final long expireTimeMs;
    private final int rfqId;
    private final long quantity;
    private final Side requesterSide;
    private final RfqState currentState;
    private final String cusip;
    private final int requesterUserId;

    public Rfq(
        final int rfqId,
        final String correlation,
        final long expireTimeMs,
        final long quantity,
        final Side requesterSide,
        final String cusip,
        final int requesterUserId)
    {
        this.rfqId = rfqId;
        this.correlation = correlation;
        this.expireTimeMs = expireTimeMs;
        this.quantity = quantity;
        this.requesterSide = requesterSide;
        this.cusip = cusip;
        this.requesterUserId = requesterUserId;
        this.currentState = RfqStateHelper.getState(RfqStates.CREATED.getStateId());
    }

    public String getCorrelation()
    {
        return correlation;
    }

    public long getExpireTimeMs()
    {
        return expireTimeMs;
    }

    public int getRfqId()
    {
        return rfqId;
    }

    public long getQuantity()
    {
        return quantity;
    }

    public Side getRequesterSide()
    {
        return requesterSide;
    }

    public RfqState getCurrentState()
    {
        return currentState;
    }

    public String getCusip()
    {
        return cusip;
    }

    public int getRequesterUserId()
    {
        return requesterUserId;
    }

    @Override
    public String toString()
    {
        return "Rfq{" +
            "correlation='" + correlation + '\'' +
            ", expireTimeMs=" + expireTimeMs +
            ", rfqId=" + rfqId +
            ", quantity=" + quantity +
            ", requesterSide=" + requesterSide +
            ", currentState=" + currentState +
            ", cusip='" + cusip + '\'' +
            ", requesterUserId=" + requesterUserId +
            '}';
    }
}
