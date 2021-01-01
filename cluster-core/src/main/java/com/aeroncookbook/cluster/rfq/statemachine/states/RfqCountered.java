/*
 * Copyright 2019-2021 Shaun Laurens.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aeroncookbook.cluster.rfq.statemachine.states;

public class RfqCountered implements RfqState
{
    public static final RfqCountered INSTANCE = new RfqCountered();

    @Override
    public RfqStates getCurrentState()
    {
        return RfqStates.COUNTERED;
    }

    @Override
    public short getCurrentStateId()
    {
        return RfqStates.COUNTERED.getStateId();
    }

    @Override
    public boolean canTransitionTo(RfqStates newState)
    {
        return newState == RfqStates.COUNTERED
            || newState == RfqStates.ACCEPTED
            || newState == RfqStates.REJECTED
            || newState == RfqStates.EXPIRED;
    }

    @Override
    public RfqState transitionTo(RfqStates newState)
    {
        if (newState == RfqStates.EXPIRED)
        {
            return RfqExpired.INSTANCE;
        }
        else if (newState == RfqStates.ACCEPTED)
        {
            return RfqAccepted.INSTANCE;
        }
        else if (newState == RfqStates.REJECTED)
        {
            return RfqRejected.INSTANCE;
        }
        else if (newState == RfqStates.COUNTERED)
        {
            return RfqCountered.INSTANCE;
        }

        return null;
    }
}
