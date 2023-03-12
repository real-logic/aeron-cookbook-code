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

import io.aeron.cluster.service.ClientSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages client sessions within the cluster
 */
public class ClientSessions
{
    private final List<ClientSession> allSessions = new ArrayList<>();

    /**
     * Adds a client session
     * @param session the session to add
     */
    public void addSession(final ClientSession session)
    {
        allSessions.add(session);
    }

    /**
     * Removes a client session
     * @param session the session to remove
     */
    public void removeSession(final ClientSession session)
    {
        allSessions.remove(session);
    }

    /**
     * Gets all client sessions known
     * @return the list of client sessions
     */
    public List<ClientSession> getAllSessions()
    {
        return allSessions;
    }
}
