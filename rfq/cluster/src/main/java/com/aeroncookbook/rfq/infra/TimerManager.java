/*
 * Copyright 2023 Adaptive Financial Consulting
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

package com.aeroncookbook.rfq.infra;

import io.aeron.cluster.service.Cluster;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Manages timers within the cluster
 */
public class TimerManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TimerManager.class);
    private final SessionMessageContextImpl context;
    private Cluster cluster;

    private final Long2ObjectHashMap<Runnable> correlationIdToRunnable = new Long2ObjectHashMap<>();

    private long correlationId = 0;

    /**
     * Constructor, accepting the context to update the cluster timestamp
     * @param context the context to update the cluster timestamp
     */
    public TimerManager(final SessionMessageContextImpl context)
    {
        this.context = context;
    }

    /**
     * Schedules a timer
     *
     * @param deadline the deadline of the timer
     * @param timerRunnable the timerRunnable to perform when the timer fires
     * @return the correlation id of the timer
     */
    public long scheduleTimer(final long deadline, final Runnable timerRunnable)
    {
        correlationId++;
        Objects.requireNonNull(cluster, "Cluster must be set before scheduling timers");
        correlationIdToRunnable.put(correlationId, timerRunnable);

        cluster.idleStrategy().reset();
        while (!cluster.scheduleTimer(correlationId, deadline))
        {
            cluster.idleStrategy().idle();
        }
        return correlationId;
    }

    /**
     * Restores a timer that the cluster has snapshotted the timer state, but not the timer manager internal state
     * @param timerCorrelationId the correlation id of the timer
     * @param task the task to perform when the timer fires
     */
    public void restoreTimer(final long timerCorrelationId, final Runnable task)
    {
        correlationIdToRunnable.put(timerCorrelationId, task);
    }

    /**
     * Called when a timer cluster event occurs
     * @param correlationId the cluster timer id
     * @param timestamp     the timestamp the timer was fired at
     */
    public void onTimerEvent(final long correlationId, final long timestamp)
    {
        context.setClusterTime(timestamp);
        if (correlationIdToRunnable.containsKey(correlationId))
        {
            correlationIdToRunnable.get(correlationId).run();
            correlationIdToRunnable.remove(correlationId);
        }
        else
        {
            LOGGER.warn("Timer fired for unknown correlation id {}", correlationId);
        }
    }

    /***
     * Sets the cluster object used for scheduling timers
     * @param cluster the cluster object
     */
    public void setCluster(final Cluster cluster)
    {
        this.cluster = cluster;
    }


}
