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

package com.aeroncookbook.cluster.rsm;

import com.aeroncookbook.cluster.rsm.node.ReplicatedStateMachine;
import com.aeroncookbook.cluster.rsm.gen.AddCommand;
import com.aeroncookbook.cluster.rsm.gen.CurrentValueEvent;
import com.aeroncookbook.cluster.rsm.gen.MultiplyCommand;
import com.aeroncookbook.cluster.rsm.gen.SetCommand;
import org.agrona.ExpandableDirectByteBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.eider.util.EiderHelper.getEiderId;

public class ReplicatedStateMachineTests
{
    @Test
    public void canAdd()
    {
        final ExpandableDirectByteBuffer returnBuffer = new ExpandableDirectByteBuffer(CurrentValueEvent.BUFFER_LENGTH);
        final ExpandableDirectByteBuffer inputBuffer = new ExpandableDirectByteBuffer(AddCommand.BUFFER_LENGTH);

        final AddCommand inputCommand = new AddCommand();
        inputCommand.setUnderlyingBuffer(inputBuffer, 0);
        inputCommand.writeHeader();
        inputCommand.writeValue(600);

        final ReplicatedStateMachine underTest = new ReplicatedStateMachine();
        underTest.add(inputCommand, returnBuffer);

        final CurrentValueEvent event = new CurrentValueEvent();
        event.setUnderlyingBuffer(returnBuffer, 0);

        Assertions.assertEquals(CurrentValueEvent.EIDER_ID, getEiderId(returnBuffer, 0));
        Assertions.assertEquals(600, event.readValue());
    }

    @Test
    public void canSetMultiply()
    {
        final ExpandableDirectByteBuffer returnBuffer = new ExpandableDirectByteBuffer(CurrentValueEvent.BUFFER_LENGTH);
        final ExpandableDirectByteBuffer inputBuffer = new ExpandableDirectByteBuffer(AddCommand.BUFFER_LENGTH);
        final CurrentValueEvent event = new CurrentValueEvent();

        final SetCommand setCommand = new SetCommand();
        setCommand.setUnderlyingBuffer(inputBuffer, 0);
        setCommand.writeHeader();
        setCommand.writeValue(100);

        final ReplicatedStateMachine underTest = new ReplicatedStateMachine();
        underTest.setCurrentValue(setCommand, returnBuffer);

        event.setUnderlyingBuffer(returnBuffer, 0);

        Assertions.assertEquals(CurrentValueEvent.EIDER_ID, getEiderId(returnBuffer, 0));
        Assertions.assertEquals(100, event.readValue());

        final MultiplyCommand multiplyCommand = new MultiplyCommand();
        multiplyCommand.setUnderlyingBuffer(inputBuffer, 0);
        multiplyCommand.writeHeader();
        multiplyCommand.writeValue(250);

        underTest.multiply(multiplyCommand, returnBuffer);

        event.setUnderlyingBuffer(returnBuffer, 0);
        Assertions.assertEquals(CurrentValueEvent.EIDER_ID, getEiderId(returnBuffer, 0));
        Assertions.assertEquals(25000, event.readValue());
    }
}
