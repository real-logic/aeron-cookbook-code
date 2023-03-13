package com.aeroncookbook.rfq.domain.rfq.states;

import org.agrona.collections.Int2ObjectHashMap;

public final class RfqStateHelper
{
    private static final Int2ObjectHashMap<RfqState> STATES = buildStates();

    private RfqStateHelper()
    {
        // no instances
    }

    public static RfqState getState(final int stateId)
    {
        return STATES.get(stateId);
    }

    private static Int2ObjectHashMap<RfqState> buildStates()
    {
        final Int2ObjectHashMap<RfqState> stateMachineStates = new Int2ObjectHashMap<>();
        stateMachineStates.put(RfqStates.CREATED.getStateId(), RfqCreated.INSTANCE);
        stateMachineStates.put(RfqStates.QUOTED.getStateId(), RfqQuoted.INSTANCE);
        stateMachineStates.put(RfqStates.COUNTERED.getStateId(), RfqCountered.INSTANCE);
        stateMachineStates.put(RfqStates.ACCEPTED.getStateId(), RfqAccepted.INSTANCE);
        stateMachineStates.put(RfqStates.REJECTED.getStateId(), RfqRejected.INSTANCE);
        stateMachineStates.put(RfqStates.EXPIRED.getStateId(), RfqExpired.INSTANCE);
        stateMachineStates.put(RfqStates.CANCELED.getStateId(), RfqCanceled.INSTANCE);
        stateMachineStates.put(RfqStates.COMPLETED.getStateId(), RfqCompleted.INSTANCE);
        return stateMachineStates;
    }
}
