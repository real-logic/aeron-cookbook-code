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

package com.aeroncookbook.cluster.rsm.node;

import com.aeroncookbook.cluster.rsm.gen.AddCommand;
import com.aeroncookbook.cluster.rsm.gen.CurrentValueEvent;
import com.aeroncookbook.cluster.rsm.gen.MultiplyCommand;
import com.aeroncookbook.cluster.rsm.gen.SetCommand;
import com.aeroncookbook.cluster.rsm.gen.Snapshot;
import org.agrona.ExpandableDirectByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatedStateMachine
{
    final Snapshot snapshot = new Snapshot();
    final CurrentValueEvent currentValueEvent = new CurrentValueEvent();
    private final Logger logger = LoggerFactory.getLogger(ReplicatedStateMachine.class);
    private int currentValue;

    public void add(AddCommand addCommand, ExpandableDirectByteBuffer returnBuffer)
    {
        int addValue = addCommand.readValue();
        currentValue += addValue;
        logger.info("adding {}, value is now {}; correlation = {}", addValue, currentValue,
            addCommand.readCorrelation());
        prepareCurrentValueEvent(returnBuffer, addCommand.readCorrelation());
    }

    public void multiply(MultiplyCommand multiplyCommand, ExpandableDirectByteBuffer returnBuffer)
    {
        int multiplyValue = multiplyCommand.readValue();
        currentValue *= multiplyCommand.readValue();
        logger.info("multiplying by {}, value is now {}; correlation = {}", multiplyValue, currentValue,
            multiplyCommand.readCorrelation());
        prepareCurrentValueEvent(returnBuffer, multiplyCommand.readCorrelation());
    }

    public void setCurrentValue(SetCommand setCommand, ExpandableDirectByteBuffer returnBuffer)
    {
        int setCurrentValue = setCommand.readValue();
        currentValue = setCurrentValue;
        logger.info("setting value to {}; correlation = {}", setCurrentValue, setCommand.readCorrelation());
        prepareCurrentValueEvent(returnBuffer, setCommand.readCorrelation());
    }

    public void takeSnapshot(ExpandableDirectByteBuffer buffer)
    {
        snapshot.setUnderlyingBuffer(buffer, 0);
        snapshot.writeHeader();
        logger.info("taking snapshot with current value at {}", currentValue);
        snapshot.writeValue(currentValue);
    }

    public void loadFromSnapshot(Snapshot snapshot)
    {
        currentValue = snapshot.readValue();
        logger.info("reading snapshot with current value at {}", currentValue);
    }

    private void prepareCurrentValueEvent(ExpandableDirectByteBuffer returnBuffer, int correlation)
    {
        currentValueEvent.setUnderlyingBuffer(returnBuffer, 0);
        currentValueEvent.writeHeader();
        currentValueEvent.writeValue(currentValue);
        currentValueEvent.writeCorrelation(correlation);
    }
}
