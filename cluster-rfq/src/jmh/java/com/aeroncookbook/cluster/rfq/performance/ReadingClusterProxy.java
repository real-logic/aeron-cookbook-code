/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.cluster.rfq.performance;

import com.aeroncookbook.cluster.rfq.gen.RfqCreatedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqQuotedEvent;
import com.aeroncookbook.cluster.rfq.statemachine.ClusterProxy;
import io.eider.util.EiderHelper;
import org.agrona.DirectBuffer;

class ReadingClusterProxy implements ClusterProxy
{
    final RfqCreatedEvent rfqCreatedEvent = new RfqCreatedEvent();
    final RfqQuotedEvent rfqQuotedEvent = new RfqQuotedEvent();
    int replyCount;
    int broadcastCount;
    int lastRfqId = -1;
    int lastRfqQuoteId = -1;

    public int getLastRfqQuoteId()
    {
        return lastRfqQuoteId;
    }

    @Override
    public void reply(final DirectBuffer buffer, final int offset, final int length)
    {
        replyCount += 1;
    }

    public int getLastRfqId()
    {
        return lastRfqId;
    }

    @Override
    public void broadcast(final DirectBuffer buffer, final int offset, final int length)
    {
        final short eiderId = EiderHelper.getEiderId(buffer, offset);
        if (eiderId == RfqCreatedEvent.EIDER_ID)
        {
            rfqCreatedEvent.setUnderlyingBuffer(buffer, offset);
            lastRfqId = rfqCreatedEvent.readRfqId();
        }
        else if (eiderId == RfqQuotedEvent.EIDER_ID)
        {
            rfqQuotedEvent.setUnderlyingBuffer(buffer, offset);
            lastRfqId = rfqQuotedEvent.readRfqId();
            lastRfqQuoteId = rfqQuotedEvent.readRfqQuoteId();
        }
        else
        {
            lastRfqId = -1;
            lastRfqQuoteId = -1;
        }
        broadcastCount += 1;
    }

    @Override
    public void scheduleExpiry(final long noSoonerThanMs, final int rfqId)
    {
        //
    }

}
