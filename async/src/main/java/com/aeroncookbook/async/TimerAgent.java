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

package com.aeroncookbook.async;

import com.aeroncookbook.cluster.async.sbe.CancelTimerCommandDecoder;
import com.aeroncookbook.cluster.async.sbe.ExitCommandDecoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderEncoder;
import com.aeroncookbook.cluster.async.sbe.NewTimerCommandDecoder;
import com.aeroncookbook.cluster.async.sbe.TimerCanceledEventEncoder;
import com.aeroncookbook.cluster.async.sbe.TimerFiredEventEncoder;
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

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final NewTimerCommandDecoder newTimer = new NewTimerCommandDecoder();
    private final CancelTimerCommandDecoder cancelTimer = new CancelTimerCommandDecoder();
    private final TimerCanceledEventEncoder timerCanceledEvent = new TimerCanceledEventEncoder();
    private final TimerFiredEventEncoder timerFiredEvent = new TimerFiredEventEncoder();

    private final ExpandableDirectByteBuffer responseBuffer;

    public TimerAgent(
        final Subscription subscription,
        final Publication timerClientPublication,
        final ShutdownSignalBarrier barrier)
    {
        this.subscription = subscription;
        this.timerClientPublication = timerClientPublication;
        this.barrier = barrier;
        this.responseBuffer = new ExpandableDirectByteBuffer(128);
    }

    @Override
    public int doWork() throws Exception
    {
        subscription.poll(this::handler, 1);
        checkTimers();
        return 0;
    }

    private void handler(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        final int templateId = messageHeaderDecoder.templateId();
        switch (templateId)
        {
            case NewTimerCommandDecoder.TEMPLATE_ID ->
            {
                newTimer.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                addTimer(newTimer.correlation(), newTimer.deadline());
            }
            case CancelTimerCommandDecoder.TEMPLATE_ID ->
            {
                cancelTimer.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                removeTimer(cancelTimer.correlation());
            }
            case ExitCommandDecoder.TEMPLATE_ID -> barrier.signal();
            default -> logger.warn("unknown command {}", templateId);
        }
    }

    private void checkTimers()
    {
        final long nowMs = epochClock.time();

        for (final TimerItem timerItem : timerItems)
        {
            if (timerItem.deadlineMs() <= nowMs)
            {
                emitTimerFired(timerItem.correlation());
            }
        }

        timerItems.removeIf(timerItem -> timerItem.deadlineMs() <= nowMs);
    }

    private void addTimer(final long correlation, final long deadline)
    {
        final TimerItem newItem = new TimerItem(correlation, deadline);
        logger.info("New timer with correlation {} for time {}", correlation, deadline);
        timerItems.add(newItem);
    }

    private void removeTimer(final long correlation)
    {
        logger.info("Remove timer with correlation {}", correlation);
        timerItems.removeIf(timerItem -> timerItem.correlation() == correlation);
        emitTimerCanceled(cancelTimer.correlation());
    }

    private void emitTimerFired(final long correlation)
    {
        logger.info("Timer has fired! correlation {}", correlation);

        awaitConnected();

        messageHeaderEncoder.wrap(responseBuffer, 0);

        timerFiredEvent.wrapAndApplyHeader(responseBuffer, 0, messageHeaderEncoder);
        timerFiredEvent.correlation(correlation);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = timerClientPublication.offer(responseBuffer, 0,
                MessageHeaderEncoder.ENCODED_LENGTH + timerFiredEvent.encodedLength());
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void emitTimerCanceled(final long correlation)
    {
        awaitConnected();

        messageHeaderEncoder.wrap(responseBuffer, 0);
        timerCanceledEvent.wrapAndApplyHeader(responseBuffer, 0, messageHeaderEncoder);
        timerCanceledEvent.correlation(correlation);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = timerClientPublication.offer(responseBuffer, 0,
                MessageHeaderEncoder.ENCODED_LENGTH + timerCanceledEvent.encodedLength());
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
