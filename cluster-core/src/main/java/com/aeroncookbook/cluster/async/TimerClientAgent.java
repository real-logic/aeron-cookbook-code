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
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.SystemEpochClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.util.EiderHelper.getEiderId;

public class TimerClientAgent implements Agent
{
    private static final int RETRY_COUNT = 3;
    private static final long TWO_SECONDS = 2_000;
    private static final long TEN_MILLISECONDS = 10;
    private static final long EXIT_CORRELATION = 1000;
    private static final long NORMAL_CORRELATION_START = 5000;
    private static final long RACE_CORRELATION_START = 10000;

    private final Publication publication;
    private final Subscription timerClientSubscription;
    private final EpochClock epochClock = new SystemEpochClock();
    private final Logger logger = LoggerFactory.getLogger(TimerClientAgent.class);

    private final ExpandableDirectByteBuffer requestBuffer;

    private final IdleStrategy internalIdle = new SleepingMillisIdleStrategy(1);

    private final NewTimerCommand newTimer = new NewTimerCommand();
    private final CancelTimerCommand cancelTimer = new CancelTimerCommand();
    private final ExitCommand exit = new ExitCommand();
    private final TimerCanceledEvent timerCanceledEvent = new TimerCanceledEvent();
    private final TimerFiredEvent timerFiredEvent = new TimerFiredEvent();

    private State agentState;
    private long normalCorrelation = NORMAL_CORRELATION_START;
    private long raceCorrelation = RACE_CORRELATION_START;

    public TimerClientAgent(final Publication publication,
                            final Subscription timerClientSubscription)
    {
        this.publication = publication;
        this.timerClientSubscription = timerClientSubscription;
        this.agentState = State.INIT;
        this.requestBuffer =
            new ExpandableDirectByteBuffer(Math.max(NewTimerCommand.BUFFER_LENGTH, CancelTimerCommand.BUFFER_LENGTH));
    }

    @Override
    public int doWork()
    {
        final long nowMs = epochClock.time();

        switch (agentState)
        {
            case INIT:
                scheduleExit();
                agentState = State.NORMAL;
                break;
            case NORMAL:
                scheduleTimer(normalCorrelation, nowMs + TEN_MILLISECONDS);
                removeTimer(normalCorrelation);
                normalCorrelation += 1;
                break;
            case RACE:
                scheduleTimer(raceCorrelation, nowMs);
                removeTimer(raceCorrelation);
                raceCorrelation += 1;
                break;
            default:
                break;
        }

        timerClientSubscription.poll(this::handler, 1);

        return 0;
    }

    private void handler(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);
        switch (eiderId)
        {
            case TimerFiredEvent.EIDER_ID:
                timerFiredEvent.setUnderlyingBuffer(buffer, offset);
                acceptTimerFired(timerFiredEvent);
                break;
            case TimerCanceledEvent.EIDER_ID:
                timerCanceledEvent.setUnderlyingBuffer(buffer, offset);
                acceptTimerCanceled(timerCanceledEvent);
                break;
            default:
                internalIdle.idle();
                break;
        }
    }

    private void scheduleExit()
    {
        logger.info("Scheduling exit with correlation {}", EXIT_CORRELATION);
        scheduleTimer(EXIT_CORRELATION, epochClock.time() + TWO_SECONDS);
    }

    private void scheduleTimer(long correlation, long deadlineMs)
    {
        logger.info("Scheduling timer with correlation {} for deadline {}", correlation, deadlineMs);
        awaitConnected();

        newTimer.setBufferWriteHeader(requestBuffer, 0);
        newTimer.writeCorrelation(correlation);
        newTimer.writeDeadline(deadlineMs);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = publication.offer(requestBuffer, 0, NewTimerCommand.BUFFER_LENGTH);
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void removeTimer(long correlation)
    {
        logger.info("Canceling timer with correlation {}", correlation);
        awaitConnected();

        cancelTimer.setBufferWriteHeader(requestBuffer, 0);
        cancelTimer.writeCorrelation(correlation);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = publication.offer(requestBuffer, 0, CancelTimerCommand.BUFFER_LENGTH);
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void awaitConnected()
    {
        while (!publication.isConnected())
        {
            internalIdle.idle();
        }
    }

    private void acceptTimerFired(TimerFiredEvent timerFiredEvent)
    {
        logger.info("Timer Fired Event! with correlation {}", timerFiredEvent.readCorrelation());
        if (timerFiredEvent.readCorrelation() == EXIT_CORRELATION)
        {
            sendExit();
        } else if (timerFiredEvent.readCorrelation() >= RACE_CORRELATION_START)
        {
            this.agentState = State.AWAITING_EXIT;
        }
    }

    private void sendExit()
    {
        logger.info("Sending exit command to Timer");
        awaitConnected();

        exit.setBufferWriteHeader(requestBuffer, 0);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = publication.offer(requestBuffer, 0, ExitCommand.BUFFER_LENGTH);
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void acceptTimerCanceled(TimerCanceledEvent timerCanceledEvent)
    {
        logger.info("Timer Canceled Event! with correlation {}", timerCanceledEvent.readCorrelation());
        if (timerCanceledEvent.readCorrelation() >= NORMAL_CORRELATION_START)
        {
            this.agentState = State.RACE;
        }
    }

    @Override
    public String roleName()
    {
        return "timer-client";
    }

    enum State
    {
        INIT,
        NORMAL,
        RACE,
        AWAITING_EXIT
    }

}
