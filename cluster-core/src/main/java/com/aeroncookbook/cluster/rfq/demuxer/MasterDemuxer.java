/*
 * Copyright 2019-2021 Shaun Laurens.
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

import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.util.EiderHelper.getEiderGroupId;

public class MasterDemuxer implements FragmentHandler
{
    public static final short RFQS = 4;
    public static final short INSTRUMENTS = 3;

    private final InstrumentDemuxer instrumentDemuxer;
    private final RfqDemuxer rfqDemuxer;
    private final Logger log = LoggerFactory.getLogger(MasterDemuxer.class);

    public MasterDemuxer(Rfqs rfqs, Instruments instruments)
    {
        instrumentDemuxer = new InstrumentDemuxer(instruments);
        rfqDemuxer = new RfqDemuxer(rfqs);
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderGroupId = getEiderGroupId(buffer, offset);
        switch (eiderGroupId)
        {
            case RFQS:
                rfqDemuxer.onFragment(buffer, offset, length, header);
                break;
            case INSTRUMENTS:
                instrumentDemuxer.onFragment(buffer, offset, length, header);
                break;
            default:
                log.warn("unknown group {}", eiderGroupId);
        }
    }

    public void setSession(ClientSession session)
    {
        this.instrumentDemuxer.setSession(session);
        this.rfqDemuxer.setSession(session);
    }

    public void setClusterTime(long timestamp)
    {
        this.instrumentDemuxer.setClusterTime(timestamp);
        this.rfqDemuxer.setClusterTime(timestamp);
    }
}
