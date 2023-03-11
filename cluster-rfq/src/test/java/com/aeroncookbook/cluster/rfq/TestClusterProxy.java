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

package com.aeroncookbook.cluster.rfq;

import com.aeroncookbook.cluster.rfq.statemachine.ClusterProxy;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class TestClusterProxy implements ClusterProxy
{
    long currentTime;
    List<DirectBuffer> replies = new ArrayList<>();
    List<DirectBuffer> broadcasts = new ArrayList<>();
    List<ExpiryTask> expiryTasks = new ArrayList<>();

    @Override
    public void reply(final DirectBuffer buffer, final int offset, final int length)
    {
        final MutableDirectBuffer toAdd = new ExpandableArrayBuffer(length - offset);
        buffer.getBytes(offset, toAdd, 0, length);
        replies.add(buffer);
    }

    @Override
    public void broadcast(final DirectBuffer buffer, final int offset, final int length)
    {
        final MutableDirectBuffer toAdd = new ExpandableArrayBuffer(length - offset);
        buffer.getBytes(offset, toAdd, 0, length);
        broadcasts.add(buffer);
    }

    @Override
    public void scheduleExpiry(final long noSoonerThanMs, final int rfqId)
    {
        expiryTasks.add(new ExpiryTask(noSoonerThanMs, rfqId));
    }

    public List<DirectBuffer> getReplies()
    {
        return replies;
    }

    public List<DirectBuffer> getBroadcasts()
    {
        return broadcasts;
    }

    public void clear()
    {
        replies.clear();
        broadcasts.clear();
    }

    public void clearTiming()
    {
        expiryTasks.clear();
    }

    public List<ExpiryTask> expiryTasksAt(final long timeMs)
    {
        return expiryTasks.stream().filter(expiryTask -> expiryTask.expireNoSoonerThanMs <= timeMs)
            .collect(Collectors.toList());
    }

    class ExpiryTask
    {

        final long expireNoSoonerThanMs;
        final int rfqId;

        ExpiryTask(final long expireNoSoonerThanMs, final int rfqId)
        {
            this.expireNoSoonerThanMs = expireNoSoonerThanMs;
            this.rfqId = rfqId;
        }
    }

}
