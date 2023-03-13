/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.cluster.rsm;

import com.aeroncookbook.cluster.rsm.node.ReplicatedStateMachine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class ReplicatedStateMachineTests
{
    @Test
    public void canAdd()
    {
        final ReplicatedStateMachine underTest = new ReplicatedStateMachine();
        underTest.add(1, 1);
        Assertions.assertEquals(1, underTest.getCurrentValue());
    }

    @Test
    public void canSetMultiply()
    {
        final ReplicatedStateMachine underTest = new ReplicatedStateMachine();
        underTest.setCurrentValue(1, 100);
        Assertions.assertEquals(100, underTest.getCurrentValue());

        underTest.multiply(2, 250);

        Assertions.assertEquals(25000, underTest.getCurrentValue());
    }
}
