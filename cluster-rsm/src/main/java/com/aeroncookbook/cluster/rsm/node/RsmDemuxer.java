/*
 * Copyright 2019-2023 Shaun Laurens.
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

import com.aeroncookbook.cluster.async.sbe.AddCommandDecoder;
import com.aeroncookbook.cluster.async.sbe.CurrentValueEventEncoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.async.sbe.MessageHeaderEncoder;
import com.aeroncookbook.cluster.async.sbe.MultiplyCommandDecoder;
import com.aeroncookbook.cluster.async.sbe.SetCommandDecoder;
import com.aeroncookbook.cluster.async.sbe.SnapshotDecoder;
import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RsmDemuxer implements FragmentHandler
{
    private final ReplicatedStateMachine stateMachine;
    private final AddCommandDecoder addCommand;
    private final MultiplyCommandDecoder multiplyCommand;
    private final SetCommandDecoder setCommand;
    private final CurrentValueEventEncoder currentValue;
    private final SnapshotDecoder snapshotDecoder;
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final Logger logger = LoggerFactory.getLogger(RsmDemuxer.class);
    private final ExpandableDirectByteBuffer returnBuffer;

    private ClientSession session;

    public RsmDemuxer(final ReplicatedStateMachine stateMachine)
    {
        this.stateMachine = stateMachine;
        this.returnBuffer = new ExpandableDirectByteBuffer(128);
        addCommand = new AddCommandDecoder();
        currentValue = new CurrentValueEventEncoder();
        multiplyCommand = new MultiplyCommandDecoder();
        setCommand = new SetCommandDecoder();
        snapshotDecoder = new SnapshotDecoder();
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        final int templateId = messageHeaderDecoder.templateId();
        switch (templateId)
        {
            case AddCommandDecoder.TEMPLATE_ID ->
            {
                addCommand.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                stateMachine.add(addCommand.correlation(), addCommand.value());
                emitCurrentValue(returnBuffer, addCommand.correlation());
            }
            case MultiplyCommandDecoder.TEMPLATE_ID ->
            {
                multiplyCommand.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                stateMachine.multiply(multiplyCommand.correlation(), multiplyCommand.value());
                emitCurrentValue(returnBuffer, multiplyCommand.correlation());
            }
            case SetCommandDecoder.TEMPLATE_ID ->
            {
                setCommand.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                stateMachine.setCurrentValue(setCommand.correlation(), setCommand.value());
                emitCurrentValue(returnBuffer, setCommand.correlation());
            }
            case SnapshotDecoder.TEMPLATE_ID ->
            {
                snapshotDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                stateMachine.loadFromSnapshot(snapshotDecoder.value());
            }
            default -> logger.error("Unknown message {}", templateId);
        }
    }

    public void setSession(final ClientSession session)
    {
        this.session = session;
    }

    private void emitCurrentValue(final ExpandableDirectByteBuffer buffer, final long correlationId)
    {
        messageHeaderEncoder.wrap(returnBuffer, 0);
        currentValue.wrapAndApplyHeader(returnBuffer, 0, messageHeaderEncoder);
        currentValue.correlation(correlationId);
        currentValue.value(stateMachine.getCurrentValue());
        session.offer(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + currentValue.encodedLength());
    }
}
