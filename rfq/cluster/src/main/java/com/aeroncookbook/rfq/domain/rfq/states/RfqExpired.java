/*
 * Copyright 2019-2023 Adaptive Financial Consulting Ltd.
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

public class RfqExpired implements RfqState
{
    public static final RfqExpired INSTANCE = new RfqExpired();

    @Override
    public RfqStates getCurrentState()
    {
        return RfqStates.EXPIRED;
    }

    @Override
    public short getCurrentStateId()
    {
        return RfqStates.EXPIRED.getStateId();
    }

    @Override
    public boolean canTransitionTo(final RfqStates newState)
    {
        return false; //terminal state
    }

    @Override
    public RfqState transitionTo(final RfqStates newState)
    {
        return null;
    }
}
