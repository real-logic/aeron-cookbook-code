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

package com.aeroncookbook.agrona;

import org.agrona.concurrent.CachedEpochClock;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.EpochMicroClock;
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.OffsetEpochNanoClock;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.SystemEpochMicroClock;
import org.agrona.concurrent.SystemEpochNanoClock;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ClockTests
{
    @Test
    public void systemEpochClock()
    {
        final EpochClock clock = SystemEpochClock.INSTANCE;
        final long time = clock.time();
        assertNotEquals(0L, time);
    }

    @Test
    public void cachedEpochClock()
    {
        final CachedEpochClock clock = new CachedEpochClock();
        clock.update(1L);

        assertEquals(1L, clock.time());

        clock.update(2L);
        assertEquals(2L, clock.time());

        clock.advance(98L);
        assertEquals(100L, clock.time());
    }

    @Test
    public void systemEpochMicroClock()
    {
        final EpochMicroClock clock = new SystemEpochMicroClock();
        final long time = clock.microTime();
        assertNotEquals(0L, time);
    }

    @Test
    public void systemEpochNanoClock()
    {
        final EpochNanoClock clock = new SystemEpochNanoClock();
        final long time = clock.nanoTime();
        assertNotEquals(0L, time);
    }

    @Test
    public void offsetEpochNanoClock()
    {
        final EpochNanoClock clock = new OffsetEpochNanoClock();
        final long time = clock.nanoTime();
        assertNotEquals(0L, time);
    }


    @Test
    public void rollovers()
    {
        System.out.println(new Date(Integer.MAX_VALUE * 1000L)); //epoch seconds: Mon Jan 18 22:14:07 EST 2038
        System.out.println(new Date(Long.MAX_VALUE)); //epoch millis: Sun Aug 17 02:12:55 EST 292278994
    }
}
