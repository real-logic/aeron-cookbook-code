/*
 * Copyright 2019-2020 Shaun Laurens.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aeroncookbook.cluster.rfq.demuxer;

import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.Helper.EiderHelper.getEiderId;

public class RfqDemuxer implements FragmentHandler
{
    private final Rfqs rfqs;
    private final CreateRfqCommand createRfqCommand;
    private final Logger log = LoggerFactory.getLogger(RfqDemuxer.class);

    public RfqDemuxer(Rfqs rfqs)
    {
        this.rfqs = rfqs;
        createRfqCommand = new CreateRfqCommand();
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);
        switch (eiderId)
        {
            case CreateRfqCommand.EIDER_ID:
                createRfqCommand.setUnderlyingBuffer(buffer, offset);
                rfqs.createRfq(createRfqCommand);
                break;
            default:
                log.warn("unknown type {}", eiderId);
        }
    }
}
