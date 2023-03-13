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

import io.aeron.Publication;
import io.aeron.cluster.service.ClientSession;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * The context for a single cluster session message
 */
public class SessionMessageContextImpl implements SessionMessageContext
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionMessageContextImpl.class);
    private static final long RETRY_COUNT = 3;
    private IdleStrategy idleStrategy;
    private final ClientSessions clientSessions;
    private long timestamp;
    private ClientSession session;

    /**
     * Constructor
     * @param clientSessions the client session store
     */
    public SessionMessageContextImpl(final ClientSessions clientSessions)
    {
        this.clientSessions = clientSessions;
    }

    /**
     * Sets the session context for this cluster message
     *
     * @param session   the session
     * @param timestamp the timestamp
     */
    public void setSessionContext(final ClientSession session, final long timestamp)
    {
        this.timestamp = timestamp;
        this.session = session;
    }

    /**
     * Sets the cluster timestamp for the current context
     * @param timestamp
     */
    public void setClusterTime(final long timestamp)
    {
        this.timestamp = timestamp;
    }

    /**
     * Gets the current cluster time, as provided by the cluster
     *
     * @return the cluster time at the time this message was received
     */
    @Override
    public long getClusterTime()
    {
        return timestamp;
    }

    /**
     * Sets the idle strategy to be used during offers
     * @param idleStrategy the idle strategy to be used
     */
    public void setIdleStrategy(final IdleStrategy idleStrategy)
    {
        this.idleStrategy = idleStrategy;
    }

    /**
     * Replies to the sender of the current session message, with retry. Disconnects a client that failed to offer
     * @param buffer the buffer to read data from
     * @param offset the offset to read from
     * @param length the length to read
     */
    @Override
    public void reply(final DirectBuffer buffer, final int offset, final int length)
    {
        offerToSession(session, buffer, offset, length);
    }

    /**
     * Broadcasts a message to all connected sessions. If the offer fails to any session after a number of retries,
     * then that session is disconnected.
     * @param buffer the buffer to read data from
     * @param offset the offset to read from
     * @param length the length to read
     */
    @Override
    public void broadcast(final DirectBuffer buffer, final int offset, final int length)
    {
        clientSessions.getAllSessions().forEach(clientSession -> offerToSession(clientSession, buffer, offset, length));
    }


    /**
     * Sends a message to a specific session, with retry.
     * Disconnects a client that failed to offer after RETRY_COUNT retries
     * @param sessionId the session to send to
     * @param buffer the buffer to read data from
     * @param offset the offset to read from
     * @param length the length to read
     */
    public void offerToSessionById(final long sessionId, final DirectBuffer buffer, final int offset, final int length)
    {
        final ClientSession clientSession = clientSessions.getById(sessionId);
        if (clientSession != null)
        {
            offerToSession(clientSession, buffer, offset, length);
        }
    }

    /**
     * Offers a message to a session, with retry. Disconnects a client that failed to offer after RETRY_COUNT retries
     * @param targetSession the session to offer to
     * @param buffer the buffer to read data from
     * @param offset the offset to read from
     * @param length the length to read
     */
    private void offerToSession(
        final ClientSession targetSession,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        Objects.requireNonNull(idleStrategy, "idleStrategy must be set");
        int retries = 0;
        do
        {
            final long result = targetSession.offer(buffer, offset, length);
            if (result > 0L)
            {
                return;
            }
            else if (result == Publication.ADMIN_ACTION || result == Publication.BACK_PRESSURED)
            {
                LOGGER.warn("backpressure or admin action on session offer");
            }
            else if (result == Publication.NOT_CONNECTED || result == Publication.MAX_POSITION_EXCEEDED)
            {
                LOGGER.error("unexpected state on session offer: {}", result);
                return;
            }

            idleStrategy.idle();
            retries += 1;
        }
        while (retries < RETRY_COUNT);

        LOGGER.error("failed to offer snapshot within {} retries. Closing client session.", RETRY_COUNT);
        session.close();
    }
}
