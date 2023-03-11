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

package com.aeroncookbook.lamport;

public class RunLamportClock
{

    public static final String EVENT_A = "Event A";
    public static final String EVENT_B = "Event B";
    public static final String EVENT_C = "Event C";
    public static final String EVENT_D = "Event D";
    public static final String EVENT_E = "Event E";

    public static void main(final String[] args)
    {
        final Process a = new Process("Process A");
        final Process b = new Process("Process B");
        final Process c = new Process("Process C");

        b.whenReceiving(EVENT_A).thenSend(EVENT_B).toProcess(c);
        c.whenReceiving(EVENT_B).thenSend(EVENT_E).toProcess(a);
        c.whenReceiving(EVENT_C).thenSend(EVENT_D).toProcess(a);

        a.emit(EVENT_A, b);
        a.emit(EVENT_C, c);

        //poll the messages in approximate order matching diagram
        c.processNextMessage();
        b.processNextMessage();
        a.processNextMessage();
        c.processNextMessage();
        a.processNextMessage();
    }
}
