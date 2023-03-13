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

import com.aeroncookbook.rfq.cluster.admin.protocol.AddInstrumentEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.BooleanType;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderEncoder;
import org.agrona.ExpandableArrayBuffer;
import picocli.CommandLine;

/**
 * Adds an instrument to the cluster
 */
@CommandLine.Command(name = "instrument-add", mixinStandardHelpOptions = false,
    description = "Adds an instrument to the cluster")
public class InstrumentAdd implements Runnable
{
    @CommandLine.ParentCommand
    CliCommands parent;

    @SuppressWarnings("all")
    @CommandLine.Option(names = "cusip", description = "Instrument CUSIP")
    private String cusip = "";

    @SuppressWarnings("all")
    @CommandLine.Option(names = "min-quantity", description = "Minimum quantity for this instrument, default 100")
    private Integer minSize = 100;

    @SuppressWarnings("all")
    @CommandLine.Option(names = "enabled", description = "True if enabled, false if not. Default true")
    private String enabled = "true";


    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(1024);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final AddInstrumentEncoder addInstrumentEncoder = new AddInstrumentEncoder();
    /**
     * Determines if a participant should be added
     */
    public void run()
    {
        addInstrumentEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        addInstrumentEncoder.cusip(cusip);
        addInstrumentEncoder.enabled(parseBool(enabled));
        addInstrumentEncoder.minSize(minSize);
        parent.offerRingBufferMessage(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH +
            addInstrumentEncoder.encodedLength());
    }

    private BooleanType parseBool(final String enabled)
    {
        return Boolean.parseBoolean(enabled) ? BooleanType.TRUE : BooleanType.FALSE;
    }


}
