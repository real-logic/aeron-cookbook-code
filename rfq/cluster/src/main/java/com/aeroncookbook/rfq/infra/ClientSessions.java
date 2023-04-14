/*
 * Copyright 2023 Adaptive Financial Consulting Ltd
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

import io.aeron.cluster.service.ClientSession;
import org.agrona.collections.Long2ObjectHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages client sessions within the cluster
 */
public class ClientSessions
{
    private final List<ClientSession> allSessions = new ArrayList<>();
    private final Long2ObjectHashMap<ClientSession> sessionsById = new Long2ObjectHashMap<>();

    private ClientSessionListener clientSessionListener;

    /**
     * Sets the client session listener
     * @param clientSessionListener the listener
     */
    public void setClientSessionListener(final ClientSessionListener clientSessionListener)
    {
        this.clientSessionListener = clientSessionListener;
    }

    /**
     * Adds a client session
     * @param session the session to add
     * @param timestamp the timestamp of the session
     */
    public void addSession(final ClientSession session, final long timestamp)
    {
        allSessions.add(session);
        sessionsById.put(session.id(), session);
        if (clientSessionListener != null)
        {
            clientSessionListener.onSessionOpen(session, timestamp);
        }
    }

    /**
     * Removes a client session
     *
     * @param session   the session to remove
     * @param timestamp
     */
    public void removeSession(final ClientSession session, final long timestamp)
    {
        allSessions.remove(session);
        sessionsById.remove(session.id());
        if (clientSessionListener != null)
        {
            clientSessionListener.onSessionClose(session, timestamp);
        }
    }

    /**
     * Gets all client sessions known
     * @return the list of client sessions
     */
    public List<ClientSession> getAllSessions()
    {
        return allSessions;
    }

    /**
     * Gets a client session by id
     * @param id the id of the session
     * @return the session, or null if not found
     */
    public ClientSession getById(final long id)
    {
        if (sessionsById.containsKey(id))
        {
            return sessionsById.get(id);
        }
        return null;
    }
}
