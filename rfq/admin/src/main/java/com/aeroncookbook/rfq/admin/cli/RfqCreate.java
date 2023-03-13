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

import com.aeroncookbook.rfq.cluster.admin.protocol.CreateRfqCommandEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.Side;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.SystemEpochClock;
import picocli.CommandLine;

import java.util.concurrent.TimeUnit;

import static com.aeroncookbook.rfq.admin.util.EnvironmentUtil.tryGetUserId;

/**
 * Creates a new RFQ
 */
@CommandLine.Command(name = "rfq-create", mixinStandardHelpOptions = false,
    description = "Creates a new RFQ")
public class RfqCreate implements Runnable
{
    @CommandLine.ParentCommand
    CliCommands parent;

    @SuppressWarnings("all")
    @CommandLine.Option(names = "cusip", description = "Instrument CUSIP")
    private String cusip = "";

    @SuppressWarnings("all")
    @CommandLine.Option(names = "quantity", description = "Quantity for this RFQ. Default 100")
    private Integer quantity = 100;

    @SuppressWarnings("all")
    @CommandLine.Option(names = "side", description = "Buy or Sell. Default buy")
    private String side = "buy";

    @SuppressWarnings("all")
    @CommandLine.Option(names = "created-by", description = "Created by user id. Defaults to USER_ID env var.")
    private Integer userId = tryGetUserId();

    private long expireTime;

    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(1024);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final CreateRfqCommandEncoder createRfqCommandEncoder = new CreateRfqCommandEncoder();
    /**
     * Determines if a participant should be added
     */
    public void run()
    {
        expireTime = SystemEpochClock.INSTANCE.time() + TimeUnit.SECONDS.toMillis(30);

        createRfqCommandEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        createRfqCommandEncoder.expireTimeMs(expireTime);
        createRfqCommandEncoder.quantity(quantity);
        createRfqCommandEncoder.requesterSide(parseSide(side));
        createRfqCommandEncoder.requesterUserId(userId);
        createRfqCommandEncoder.cusip(cusip);

        parent.offerRingBufferMessage(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH +
            createRfqCommandEncoder.encodedLength());
    }

    private Side parseSide(final String enabled)
    {
        return side.equalsIgnoreCase("buy") ? Side.BUY : Side.SELL;
    }


}
