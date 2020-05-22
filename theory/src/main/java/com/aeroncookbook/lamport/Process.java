/*
 *
 *  * Copyright 2019-2020 eleventy7
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * https://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.aeroncookbook.lamport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class Process
{
    private final String name;
    private final Map<String, Process> bus = new HashMap<>();
    private final Map<String, String> outputMessage = new HashMap<>();
    private final Queue<Message> messages = new ArrayDeque<>();
    private final Logger logger = LoggerFactory.getLogger(Process.class);
    private long time;

    private String tmpWhenReceiving;
    private String tmpThenSend;

    public Process(String name)
    {
        this.name = name;
    }

    public void send(Process dest, String message)
    {
        //update the Lamport timestamp
        time = time + 1;

        logger.info("[{}] sending {} to {}; timestamp is {}", name, message, dest.getName(), time);
        dest.onMessage(message, time);
    }

    public void onMessage(String message, long messageTime)
    {
        final Message m = new Message(messageTime, message);
        messages.add(m);
    }

    //so that we can manually controlling message polling
    public void processNextMessage()
    {
        final Message nxtMsg = messages.poll();

        //update the Lamport timestamp
        time = Long.max(nxtMsg.getTime(), time) + 1;

        logger.info("[{}] received {}; timestamp is now {}", name, nxtMsg.getMsg(), time);

        final Process toSendOn = bus.getOrDefault(nxtMsg.getMsg(), null);
        String output = outputMessage.getOrDefault(nxtMsg.getMsg(), null);
        if (toSendOn != null && output != null)
        {
            send(toSendOn, output);
        }
    }

    public void emit(String message, Process destination)
    {
        send(destination, message);
    }

    public String getName()
    {
        return name;
    }

    public Process whenReceiving(String message)
    {
        tmpWhenReceiving = message;
        return this;
    }

    public Process thenSend(String message)
    {
        tmpThenSend = message;
        return this;
    }

    public void toProcess(Process process)
    {
        outputMessage.put(tmpWhenReceiving, tmpThenSend);
        bus.put(tmpWhenReceiving, process);
        tmpThenSend = null;
        tmpWhenReceiving = null;
    }

}
