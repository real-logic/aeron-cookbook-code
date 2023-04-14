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

import com.aeroncookbook.rfq.cluster.admin.protocol.CancelRfqCommandEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderEncoder;
import org.agrona.ExpandableArrayBuffer;
import picocli.CommandLine;

import static com.aeroncookbook.rfq.admin.util.EnvironmentUtil.tryGetUserId;

/**
 * Cancels an RFQ
 */
@CommandLine.Command(name = "rfq-cancel", mixinStandardHelpOptions = false,
    description = "Cancels an RFQ. Only Requesting party can cancel")
public class RfqCancel implements Runnable
{
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(1024);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final CancelRfqCommandEncoder cancelRfqCommandEncoder = new CancelRfqCommandEncoder();
    @CommandLine.ParentCommand
    CliCommands parent;
    @SuppressWarnings("all")
    @CommandLine.Option(names = "rfq-id", description = "RFQ ID")
    private Integer rfqId = Integer.MIN_VALUE;

    @SuppressWarnings("all")
    @CommandLine.Option(names = "canceled-by", description = "Canceled by user id. Default is USER_ID env var.")
    private Integer canceledBy = tryGetUserId();

    public void run()
    {
        cancelRfqCommandEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        cancelRfqCommandEncoder.rfqId(rfqId);
        cancelRfqCommandEncoder.userId(canceledBy);

        parent.offerRingBufferMessage(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH +
            cancelRfqCommandEncoder.encodedLength());
    }

}
