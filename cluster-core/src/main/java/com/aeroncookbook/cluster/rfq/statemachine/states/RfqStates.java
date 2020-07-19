/*
 * Copyright 2019-2020 Shaun Laurens.
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

public enum RfqStates
{
    CREATED(0),
    INVITED(1),
    QUOTED(2),
    COUNTERED(3),
    ACCEPTED(4),
    REJECTED(5),
    EXPIRED(6),
    CANCELED(7),
    COMPLETED(8);

    private final int stateId;

    RfqStates(int stateId)
    {
        this.stateId = stateId;
    }

    public static RfqStates fromId(int stateId)
    {
        switch (stateId)
        {
            case 0:
                return CREATED;
            case 1:
                return INVITED;
            case 2:
                return QUOTED;
            case 3:
                return COUNTERED;
            case 4:
                return ACCEPTED;
            case 5:
                return REJECTED;
            case 6:
                return EXPIRED;
            case 7:
                return CANCELED;
            case 8:
                return COMPLETED;
            default:
                return null;
        }
    }

    public int getStateId()
    {
        return this.stateId;
    }
}
