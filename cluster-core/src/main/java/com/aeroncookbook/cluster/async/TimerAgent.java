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

package com.aeroncookbook.cluster.async;

import com.aeroncookbook.cluster.async.gen.CancelTimerCommand;
import com.aeroncookbook.cluster.async.gen.ExitCommand;
import com.aeroncookbook.cluster.async.gen.NewTimerCommand;
import com.aeroncookbook.cluster.async.gen.TimerCanceledEvent;
import com.aeroncookbook.cluster.async.gen.TimerFiredEvent;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.SystemEpochClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.eider.util.EiderHelper.getEiderId;

public class TimerAgent implements Agent
{
    private static final int RETRY_COUNT = 3;

    private final Subscription subscription;
    private final Publication timerClientPublication;
    private final ShutdownSignalBarrier barrier;
    private final EpochClock epochClock = new SystemEpochClock();
    private final Logger logger = LoggerFactory.getLogger(TimerAgent.class);
    private final List<TimerItem> timerItems = new ArrayList<>();
    private final IdleStrategy internalIdle = new SleepingMillisIdleStrategy(1);

    private final NewTimerCommand newTimer = new NewTimerCommand();
    private final CancelTimerCommand cancelTimer = new CancelTimerCommand();
    private final TimerCanceledEvent timerCanceledEvent = new TimerCanceledEvent();
    private final TimerFiredEvent timerFiredEvent = new TimerFiredEvent();

    private final ExpandableDirectByteBuffer responseBuffer;

    public TimerAgent(final Subscription subscription, final Publication timerClientPublication,
                      final ShutdownSignalBarrier barrier)
    {
        this.subscription = subscription;
        this.timerClientPublication = timerClientPublication;
        this.barrier = barrier;
        this.responseBuffer = new ExpandableDirectByteBuffer(TimerFiredEvent.BUFFER_LENGTH);
    }

    @Override
    public int doWork() throws Exception
    {
        subscription.poll(this::handler, 1);
        checkTimers();
        return 0;
    }

    private void handler(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);
        switch (eiderId)
        {
            case NewTimerCommand.EIDER_ID:
                newTimer.setUnderlyingBuffer(buffer, offset);
                addTimer(newTimer);
                break;
            case CancelTimerCommand.EIDER_ID:
                cancelTimer.setUnderlyingBuffer(buffer, offset);
                removeTimer(cancelTimer);
                break;
            case ExitCommand.EIDER_ID:
                barrier.signal();
                break;
            default:
                logger.warn("unknown command {}", eiderId);
        }
    }

    private void checkTimers()
    {
        final long nowMs = epochClock.time();

        for (TimerItem timerItem : timerItems)
        {
            if (timerItem.deadlineMs <= nowMs)
            {
                emitTimerFired(timerItem.correlation);
            }
        }

        timerItems.removeIf(timerItem -> timerItem.deadlineMs <= nowMs);
    }

    private void addTimer(NewTimerCommand newTimer)
    {
        TimerItem newItem = new TimerItem();
        newItem.correlation = newTimer.readCorrelation();
        newItem.deadlineMs = newTimer.readDeadline();
        logger.info("New timer with correlation {} for time {}", newItem.correlation, newItem.deadlineMs);
        timerItems.add(newItem);
    }

    private void removeTimer(CancelTimerCommand cancelTimer)
    {
        logger.info("Remove timer with correlation {}", cancelTimer.readCorrelation());
        timerItems.removeIf(timerItem -> timerItem.correlation == cancelTimer.readCorrelation());
        emitTimerCanceled(cancelTimer.readCorrelation());
    }

    private void emitTimerFired(long correlation)
    {
        logger.info("Timer has fired! correlation {}", correlation);
        awaitConnected();

        timerFiredEvent.setUnderlyingBuffer(responseBuffer, 0);
        timerFiredEvent.writeHeader();
        timerFiredEvent.writeCorrelation(correlation);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = timerClientPublication.offer(responseBuffer, 0, TimerFiredEvent.BUFFER_LENGTH);
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void emitTimerCanceled(long correlation)
    {
        awaitConnected();

        timerCanceledEvent.setUnderlyingBuffer(responseBuffer, 0);
        timerCanceledEvent.writeHeader();
        timerCanceledEvent.writeCorrelation(correlation);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = timerClientPublication.offer(responseBuffer, 0, TimerCanceledEvent.BUFFER_LENGTH);
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void awaitConnected()
    {
        while (!timerClientPublication.isConnected())
        {
            internalIdle.idle();
        }
    }

    @Override
    public String roleName()
    {
        return "timer";
    }
}
