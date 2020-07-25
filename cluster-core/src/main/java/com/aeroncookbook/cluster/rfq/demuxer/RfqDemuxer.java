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

import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CancelRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CounterRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RejectRfqCommand;
import com.aeroncookbook.cluster.rfq.statemachine.Rfqs;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.util.EiderHelper.getEiderId;

public class RfqDemuxer implements FragmentHandler
{
    private final Rfqs rfqs;
    private final CreateRfqCommand createRfqCommand;
    private final CancelRfqCommand cancelRfqCommand;
    private final RejectRfqCommand rejectRfqCommand;
    private final AcceptRfqCommand acceptRfqCommand;
    private final CounterRfqCommand counterRfqCommand;
    private final QuoteRfqCommand quoteRfqCommand;

    private final Logger log = LoggerFactory.getLogger(RfqDemuxer.class);
    private ClientSession session;
    private long timestamp;

    public RfqDemuxer(Rfqs rfqs)
    {
        this.rfqs = rfqs;
        createRfqCommand = new CreateRfqCommand();
        cancelRfqCommand = new CancelRfqCommand();
        rejectRfqCommand = new RejectRfqCommand();
        acceptRfqCommand = new AcceptRfqCommand();
        counterRfqCommand = new CounterRfqCommand();
        quoteRfqCommand = new QuoteRfqCommand();
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);
        switch (eiderId)
        {
            case CreateRfqCommand.EIDER_ID:
                createRfqCommand.setUnderlyingBuffer(buffer, offset);
                rfqs.createRfq(createRfqCommand, timestamp, session.id());
                break;
            case CancelRfqCommand.EIDER_ID:
                cancelRfqCommand.setUnderlyingBuffer(buffer, offset);
                rfqs.cancelRfq(cancelRfqCommand, timestamp);
                break;
            case RejectRfqCommand.EIDER_ID:
                rejectRfqCommand.setUnderlyingBuffer(buffer, offset);
                rfqs.rejectRfq(rejectRfqCommand, timestamp);
                break;
            case AcceptRfqCommand.EIDER_ID:
                acceptRfqCommand.setUnderlyingBuffer(buffer, offset);
                rfqs.acceptRfq(acceptRfqCommand, timestamp, session.id());
                break;
            case CounterRfqCommand.EIDER_ID:
                counterRfqCommand.setUnderlyingBuffer(buffer, offset);
                rfqs.counterRfq(counterRfqCommand, timestamp);
                break;
            case QuoteRfqCommand.EIDER_ID:
                quoteRfqCommand.setUnderlyingBuffer(buffer, offset);
                rfqs.quoteRfq(quoteRfqCommand, timestamp, session.id());
                break;
            default:
                log.warn("unknown type {}", eiderId);
        }
    }

    public void setSession(ClientSession session)
    {
        this.session = session;
    }

    public void setClusterTime(long timestamp)
    {
        this.timestamp = timestamp;
    }
}
