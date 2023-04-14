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

import com.aeroncookbook.rfq.cluster.admin.protocol.SetInstrumentEnabledFlagEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.BooleanType;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderEncoder;
import org.agrona.ExpandableArrayBuffer;
import picocli.CommandLine;

/**
 * Disables an instrument
 */
@CommandLine.Command(name = "instrument-disable", mixinStandardHelpOptions = false,
    description = "Disables the given instrument")
public class InstrumentDisable implements Runnable
{
    @CommandLine.ParentCommand
    CliCommands parent;

    @SuppressWarnings("all")
    @CommandLine.Option(names = "cusip", description = "Instrument CUSIP")
    private String cusip = "";

    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(1024);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final SetInstrumentEnabledFlagEncoder setInstrumentEnabled = new SetInstrumentEnabledFlagEncoder();

    public void run()
    {
        setInstrumentEnabled.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        setInstrumentEnabled.cusip(cusip);
        setInstrumentEnabled.enabled(BooleanType.FALSE);
        parent.offerRingBufferMessage(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH +
            setInstrumentEnabled.encodedLength());
    }


}
