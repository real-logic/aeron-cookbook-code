/*
 * Copyright 2019-2022 Shaun Laurens.
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

package com.aeroncookbook.cluster.rsm.node;

import com.aeroncookbook.cluster.rsm.gen.AddCommand;
import com.aeroncookbook.cluster.rsm.gen.CurrentValueEvent;
import com.aeroncookbook.cluster.rsm.gen.MultiplyCommand;
import com.aeroncookbook.cluster.rsm.gen.SetCommand;
import com.aeroncookbook.cluster.rsm.gen.Snapshot;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eider.util.EiderHelper.getEiderId;

public class RsmDemuxer implements FragmentHandler
{
    private final ReplicatedStateMachine stateMachine;
    private final AddCommand addCommand;
    private final MultiplyCommand multiplyCommand;
    private final SetCommand setCommand;
    private final Snapshot snapshot;
    private final Logger logger = LoggerFactory.getLogger(RsmDemuxer.class);
    private ExpandableDirectByteBuffer returnBuffer;

    private ClientSession session;

    public RsmDemuxer(ReplicatedStateMachine stateMachine)
    {
        this.stateMachine = stateMachine;
        this.returnBuffer = new ExpandableDirectByteBuffer(CurrentValueEvent.BUFFER_LENGTH);
        addCommand = new AddCommand();
        multiplyCommand = new MultiplyCommand();
        setCommand = new SetCommand();
        snapshot = new Snapshot();
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        short eiderId = getEiderId(buffer, offset);

        switch (eiderId)
        {
            case AddCommand.EIDER_ID:
                addCommand.setUnderlyingBuffer(buffer, offset);
                stateMachine.add(addCommand, returnBuffer);
                emitCurrentValue(returnBuffer);
                break;
            case MultiplyCommand.EIDER_ID:
                multiplyCommand.setUnderlyingBuffer(buffer, offset);
                stateMachine.multiply(multiplyCommand, returnBuffer);
                emitCurrentValue(returnBuffer);
                break;
            case SetCommand.EIDER_ID:
                setCommand.setUnderlyingBuffer(buffer, offset);
                stateMachine.setCurrentValue(setCommand, returnBuffer);
                emitCurrentValue(returnBuffer);
                break;
            case Snapshot.EIDER_ID:
                snapshot.setUnderlyingBuffer(buffer, offset);
                stateMachine.loadFromSnapshot(snapshot);
                break;
            default:
                logger.error("Unknown message {}", eiderId);
        }
    }

    public void setSession(ClientSession session)
    {
        this.session = session;
    }

    private void emitCurrentValue(ExpandableDirectByteBuffer buffer)
    {
        session.offer(buffer, 0, CurrentValueEvent.BUFFER_LENGTH);
    }
}
