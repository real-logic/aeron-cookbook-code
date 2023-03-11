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

import com.aeroncookbook.cluster.async.sbe.CancelTimerCommandEncoder;
import com.aeroncookbook.cluster.async.sbe.ExitCommandEncoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderEncoder;
import com.aeroncookbook.cluster.async.sbe.NewTimerCommandEncoder;
import com.aeroncookbook.cluster.async.sbe.TimerCanceledEventDecoder;
import com.aeroncookbook.cluster.async.sbe.TimerFiredEventDecoder;
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

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final NewTimerCommandEncoder newTimer = new NewTimerCommandEncoder();
    private final CancelTimerCommandEncoder cancelTimer = new CancelTimerCommandEncoder();
    private final ExitCommandEncoder exit = new ExitCommandEncoder();
    private final TimerCanceledEventDecoder timerCanceledEvent = new TimerCanceledEventDecoder();
    private final TimerFiredEventDecoder timerFiredEvent = new TimerFiredEventDecoder();


    private State agentState;
    private long normalCorrelation = NORMAL_CORRELATION_START;
    private long raceCorrelation = RACE_CORRELATION_START;

    public TimerClientAgent(final Publication publication, final Subscription timerClientSubscription)
    {
        this.publication = publication;
        this.timerClientSubscription = timerClientSubscription;
        this.agentState = State.INIT;
        this.requestBuffer = new ExpandableDirectByteBuffer(128);
    }

    @Override
    public int doWork()
    {
        final long nowMs = epochClock.time();

        switch (agentState)
        {
            case INIT ->
            {
                scheduleExit();
                agentState = State.NORMAL;
            }
            case NORMAL ->
            {
                scheduleTimer(normalCorrelation, nowMs + TEN_MILLISECONDS);
                removeTimer(normalCorrelation);
                normalCorrelation += 1;
            }
            case RACE ->
            {
                scheduleTimer(raceCorrelation, nowMs);
                removeTimer(raceCorrelation);
                raceCorrelation += 1;
            }
            default ->
            {
            }
        }

        timerClientSubscription.poll(this::handler, 1);

        return 0;
    }

    private void handler(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        final int templateId = messageHeaderDecoder.templateId();
        switch (templateId)
        {
            case TimerFiredEventDecoder.TEMPLATE_ID ->
            {
                timerFiredEvent.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                acceptTimerFired(timerFiredEvent.correlation());
            }
            case TimerCanceledEventDecoder.TEMPLATE_ID ->
            {
                timerCanceledEvent.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                acceptTimerCanceled(timerCanceledEvent.correlation());
            }
            default -> internalIdle.idle();
        }
    }

    private void scheduleExit()
    {
        logger.info("Scheduling exit with correlation {}", EXIT_CORRELATION);
        scheduleTimer(EXIT_CORRELATION, epochClock.time() + TWO_SECONDS);
    }

    private void scheduleTimer(final long correlation, final long deadlineMs)
    {
        logger.info("Scheduling timer with correlation {} for deadline {}", correlation, deadlineMs);

        awaitConnected();

        messageHeaderEncoder.wrap(requestBuffer, 0);
        newTimer.wrapAndApplyHeader(requestBuffer, 0, messageHeaderEncoder);

        newTimer.correlation(correlation);
        newTimer.deadline(deadlineMs);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = publication.offer(requestBuffer, 0,
                MessageHeaderEncoder.ENCODED_LENGTH + newTimer.encodedLength());
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void removeTimer(final long correlation)
    {
        logger.info("Canceling timer with correlation {}", correlation);
        awaitConnected();

        messageHeaderEncoder.wrap(requestBuffer, 0);
        cancelTimer.wrapAndApplyHeader(requestBuffer, 0, messageHeaderEncoder);
        cancelTimer.correlation(correlation);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = publication.offer(requestBuffer, 0,
                MessageHeaderEncoder.ENCODED_LENGTH + cancelTimer.encodedLength());
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

    private void acceptTimerFired(final long correlation)
    {
        logger.info("Timer Fired Event! with correlation {}", correlation);
        if (correlation == EXIT_CORRELATION)
        {
            sendExit();
        }
        else if (correlation >= RACE_CORRELATION_START)
        {
            this.agentState = State.AWAITING_EXIT;
        }
    }

    private void sendExit()
    {
        logger.info("Sending exit command to Timer");
        awaitConnected();

        messageHeaderEncoder.wrap(requestBuffer, 0);
        exit.wrapAndApplyHeader(requestBuffer, 0, messageHeaderEncoder);

        int attempts = RETRY_COUNT;
        do
        {
            final long result = publication.offer(requestBuffer, 0,
                MessageHeaderEncoder.ENCODED_LENGTH + exit.encodedLength());
            if (result > 0)
            {
                break;
            }
        }
        while (--attempts > 0);
    }

    private void acceptTimerCanceled(final long correlation)
    {
        logger.info("Timer Canceled Event! with correlation {}", correlation);
        if (correlation >= NORMAL_CORRELATION_START)
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
