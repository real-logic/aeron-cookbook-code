/*
 * Copyright 2023 Adaptive Financial Consulting
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

package com.aeroncookbook.rfq.admin.cli;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.jline.reader.LineReader;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

import java.io.PrintWriter;

/**
 * Cli Command parent
 */
@CommandLine.Command(name = "",
    description = {
        "Interactive shell. " +
            "Hit @|magenta <TAB>|@ to see available commands.",
        "Hit @|magenta ALT-S|@ to toggle tailtips.",
        ""},
    subcommands = {
        PicocliCommands.ClearScreen.class, CommandLine.HelpCommand.class,
        ConnectCluster.class, DisconnectCluster.class, InstrumentAdd.class, InstrumentDisable.class,
        InstrumentEnable.class, InstrumentList.class, RfqCreate.class, RfqCancel.class, RfqQuote.class,
        RfqCounter.class, RfqAccept.class, RfqReject.class})
public class CliCommands implements Runnable
{
    PrintWriter out;
    private OneToOneRingBuffer adminChannel;

    /**
     * Parent for all the commands
     */
    public CliCommands()
    {
    }

    /**
     * Sets the reader
     *
     * @param reader the reader
     */
    public void setReader(final LineReader reader)
    {
        out = reader.getTerminal().writer();
    }

    /**
     * Gets the usage of the commands
     */
    public void run()
    {
        out.println(new CommandLine(this).getUsageMessage());
    }

    /**
     * Cluster interaction object
     *
     * @param adminChannel the admin client
     */
    public void setAdminChannel(final OneToOneRingBuffer adminChannel)
    {
        this.adminChannel = adminChannel;
    }

    /**
     * Offers a message to the admin channel that will be passed straight to the cluster
     *
     * @param buffer        the buffer
     * @param offset        the offset
     * @param encodedLength the encoded length
     */
    public void offerRingBufferMessage(final ExpandableArrayBuffer buffer, final int offset, final int encodedLength)
    {
        final boolean success = adminChannel.write(10, buffer, offset, encodedLength);
        if (!success)
        {
            out.println("Failed to send message to cluster interaction agent. Buffer is full.");
        }
    }

}
