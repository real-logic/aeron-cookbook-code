/*
 * Copyright 2019-2022 Shaun Laurens.
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

import com.aeroncookbook.cluster.rfq.statemachine.ClusterProxy;
import io.eider.util.EiderHelper;
import org.agrona.DirectBuffer;

class PerfTestClusterProxy implements ClusterProxy
{

    short eiderIdReturned;

    @Override
    public void reply(final DirectBuffer buffer, final int offset, final int length)
    {
        eiderIdReturned = EiderHelper.getEiderId(buffer, offset);
    }

    @Override
    public void broadcast(final DirectBuffer buffer, final int offset, final int length)
    {
        eiderIdReturned = EiderHelper.getEiderId(buffer, offset);
    }

    @Override
    public void scheduleExpiry(final long noSoonerThanMs, final int rfqId)
    {
        //no action
    }

    public short getEiderIdReturned()
    {
        return eiderIdReturned;
    }

}
