/*
 * Copyright 2023 Shaun Laurens
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

/**
 * Listener for client session events
 */
public interface ClientSessionListener
{
    /**
     * Called when a client session is opened
     *
     * @param session     the session that was opened
     * @param timestampMs the timestamp of the event
     */
    void onSessionOpen(ClientSession session, long timestampMs);

    /**
     * Called when a client session is closed
     *
     * @param session     the session that was closed
     * @param timestampMs the timestamp of the event
     */
    void onSessionClose(ClientSession session, long timestampMs);
}
