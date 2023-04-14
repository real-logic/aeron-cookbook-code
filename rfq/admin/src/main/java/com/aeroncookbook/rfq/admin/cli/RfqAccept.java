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

package com.aeroncookbook.rfq.admin.cli;

import com.aeroncookbook.rfq.cluster.admin.protocol.AcceptRfqCommandEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderEncoder;
import org.agrona.ExpandableArrayBuffer;
import picocli.CommandLine;

import static com.aeroncookbook.rfq.admin.util.EnvironmentUtil.tryGetUserId;

/**
 * Counter a quoted or countered RFQ
 */
@CommandLine.Command(name = "rfq-accept", mixinStandardHelpOptions = false,
    description = "Accepts a quoted or countered RFQ")
public class RfqAccept implements Runnable
{
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(1024);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final AcceptRfqCommandEncoder acceptRfqCommandEncoder = new AcceptRfqCommandEncoder();
    @CommandLine.ParentCommand
    CliCommands parent;
    @SuppressWarnings("all")
    @CommandLine.Option(names = "rfq-id", description = "RFQ ID")
    private int rfqId = 0;
    @SuppressWarnings("all")
    @CommandLine.Option(names = "accepted-by", description = "Accepted by user id. Defaults to USER_ID env var.")
    private Integer userId = tryGetUserId();

    public void run()
    {
        acceptRfqCommandEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        acceptRfqCommandEncoder.rfqId(rfqId);
        acceptRfqCommandEncoder.userId(userId);

        parent.offerRingBufferMessage(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH +
            acceptRfqCommandEncoder.encodedLength());
    }

}
