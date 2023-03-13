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

package com.aeroncookbook.rfq.admin.cli;

import com.aeroncookbook.rfq.cluster.admin.protocol.DisconnectClusterEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderEncoder;
import org.agrona.ExpandableArrayBuffer;
import picocli.CommandLine;

/**
 * Adds a participant to the cluster
 */
@CommandLine.Command(name = "disconnect", mixinStandardHelpOptions = false,
    description = "Disconnects from the cluster")
public class DisconnectCluster implements Runnable
{
    @CommandLine.ParentCommand
    CliCommands parent;

    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(1024);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final DisconnectClusterEncoder disconnectClusterEncoder = new DisconnectClusterEncoder();

    /**
     * sends a disconnect cluster via the comms channel
     */
    public void run()
    {
        disconnectClusterEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        parent.offerRingBufferMessage(
            buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + disconnectClusterEncoder.encodedLength());
    }


}
