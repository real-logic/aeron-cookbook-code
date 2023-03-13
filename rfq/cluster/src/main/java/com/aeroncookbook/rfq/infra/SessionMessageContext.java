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

import org.agrona.DirectBuffer;

/**
 * Interface to session context data
 */
public interface SessionMessageContext
{
    /**
     * Gets the cluster time
     * @return the cluster time at the time the message was written to log
     */
    long getClusterTime();

    /**
     * Replies to the caller
     * @param buffer the buffer to read data from
     * @param offset the offset to read from
     * @param length the length to read
     */
    void reply(DirectBuffer buffer, int offset, int length);

    /**
     * Broadcast a message to all connected sessions
     * @param buffer the buffer to read data from
     * @param offset the offset to read from
     * @param length the length to read
     */
    void broadcast(DirectBuffer buffer, int offset, int length);
}

