/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.rfq.domain.rfq.states;

public class RfqQuoted implements RfqState
{
    public static final RfqQuoted INSTANCE = new RfqQuoted();

    @Override
    public RfqStates getCurrentState()
    {
        return RfqStates.QUOTED;
    }

    @Override
    public short getCurrentStateId()
    {
        return RfqStates.QUOTED.getStateId();
    }

    @Override
    public boolean canTransitionTo(final RfqStates newState)
    {
        return newState == RfqStates.ACCEPTED ||
            newState == RfqStates.COUNTERED ||
            newState == RfqStates.EXPIRED ||
            newState == RfqStates.CANCELED ||
            newState == RfqStates.REJECTED;
    }

    @Override
    public RfqState transitionTo(final RfqStates newState)
    {
        if (newState == RfqStates.ACCEPTED)
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
        else if (newState == RfqStates.EXPIRED)
        {
            return RfqExpired.INSTANCE;
        }
        else if (newState == RfqStates.CANCELED)
        {
            return RfqCanceled.INSTANCE;
        }
        return null;
    }
}
