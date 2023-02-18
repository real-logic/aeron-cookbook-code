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

package com.aeroncookbook.cluster.rfq;

import com.aeroncookbook.cluster.rfq.statemachine.ClusterProxy;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

class LastMessageTestClusterProxy implements ClusterProxy
{
    DirectBuffer lastReply;
    int replyCount;
    DirectBuffer lastBroadcast;
    int broadcastCount;

    @Override
    public void reply(final DirectBuffer buffer, final int offset, final int length)
    {
        final MutableDirectBuffer toAdd = new ExpandableArrayBuffer(length - offset);
        buffer.getBytes(offset, toAdd, 0, length);
        lastReply = buffer;
        replyCount += 1;
    }

    public int getReplyCount()
    {
        return replyCount;
    }

    public int getBroadcastCount()
    {
        return broadcastCount;
    }

    @Override
    public void broadcast(final DirectBuffer buffer, final int offset, final int length)
    {
        final MutableDirectBuffer toAdd = new ExpandableArrayBuffer(length - offset);
        buffer.getBytes(offset, toAdd, 0, length);
        lastBroadcast = buffer;
        broadcastCount += 1;
    }

    @Override
    public void scheduleExpiry(final long noSoonerThanMs, final int rfqId)
    {
        //
    }

    public DirectBuffer getLastReply()
    {
        return lastReply;
    }

    public DirectBuffer getLastBroadcast()
    {
        return lastBroadcast;
    }
}
