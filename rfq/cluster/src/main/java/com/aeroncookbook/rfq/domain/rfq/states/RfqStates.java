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

public enum RfqStates
{
    CREATED((short)0),
    QUOTED((short)1),
    COUNTERED((short)2),
    ACCEPTED((short)3),
    REJECTED((short)4),
    EXPIRED((short)5),
    CANCELED((short)6),
    COMPLETED((short)7);

    private final short stateId;

    RfqStates(final short stateId)
    {
        this.stateId = stateId;
    }

    public static RfqStates fromId(final short stateId)
    {
        switch (stateId)
        {
            case 0:
                return CREATED;
            case 1:
                return QUOTED;
            case 2:
                return COUNTERED;
            case 3:
                return ACCEPTED;
            case 4:
                return REJECTED;
            case 5:
                return EXPIRED;
            case 6:
                return CANCELED;
            case 7:
                return COMPLETED;
            default:
                return null;
        }
    }

    public short getStateId()
    {
        return this.stateId;
    }
}
