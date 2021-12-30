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

package com.aeroncookbook.cluster.rfq.timers;

import org.agrona.collections.Object2ObjectHashMap;

public class TimerService
{
    private long currentTimerId = 1;
    private Object2ObjectHashMap<Long, Integer> timerMap;

    public TimerService()
    {
        timerMap = new Object2ObjectHashMap<>();
    }

    public long getCorrelationIdForRfqId(int rfqId)
    {
        currentTimerId += 1;
        timerMap.put(currentTimerId, rfqId);
        return currentTimerId;
    }

    public int getRfqIdForCorrelationId(long timerId)
    {
        return timerMap.get(timerId);
    }
}
