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

package com.aeroncookbook.cluster.rfq.statemachine;

public enum RfqResponseType
{
    QUOTE_FROM_RESPONDER((short)0),
    REQUESTOR_ACCEPTED((short)1),
    RESPONDER_ACCEPTED((short)2),
    REQUESTOR_REJECTED((short)3),
    RESPONDER_REJECTED((short)4),
    REQUESTOR_COUNTERED((short)5),
    RESPONDER_COUNTERED((short)6);

    private final short responseTypeId;

    RfqResponseType(short responseTypeId)
    {
        this.responseTypeId = responseTypeId;
    }

    public static RfqResponseType fromId(short responseTypeId)
    {
        switch (responseTypeId)
        {
            case 0:
                return QUOTE_FROM_RESPONDER;
            case 1:
                return REQUESTOR_ACCEPTED;
            case 2:
                return RESPONDER_ACCEPTED;
            case 3:
                return REQUESTOR_REJECTED;
            case 4:
                return RESPONDER_REJECTED;
            case 5:
                return REQUESTOR_COUNTERED;
            case 6:
                return RESPONDER_COUNTERED;
            default:
                return null;
        }
    }

    public short getResponseTypeId()
    {
        return this.responseTypeId;
    }
}
