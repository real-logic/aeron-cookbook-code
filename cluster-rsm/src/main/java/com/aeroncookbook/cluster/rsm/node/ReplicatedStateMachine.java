/*
 * Copyright 2019-2023 Adaptive Financial Consulting Ltd.
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

package com.aeroncookbook.cluster.rsm.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatedStateMachine
{
    private final Logger logger = LoggerFactory.getLogger(ReplicatedStateMachine.class);
    private int currentValue;

    public void add(final long correlation, final int addValue)
    {
        currentValue += addValue;
        logger.info("adding {}, value is now {}; correlation = {}", addValue, currentValue, correlation);
    }

    public void multiply(final long correlation, final int multiplyValue)

    {
        currentValue *= multiplyValue;
        logger.info("multiplying by {}, value is now {}; correlation = {}", multiplyValue, currentValue, correlation);
    }

    public void setCurrentValue(final long correlation, final int newValue)
    {
        currentValue = newValue;
        logger.info("setting value to {}; correlation = {}", newValue, correlation);
    }

    public void loadFromSnapshot(final int currentValue)
    {
        logger.info("reading snapshot with current value at {}", currentValue);
    }

    public int getCurrentValue()
    {
        return currentValue;
    }
}
