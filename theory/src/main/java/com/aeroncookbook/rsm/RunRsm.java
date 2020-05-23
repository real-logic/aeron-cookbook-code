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

package com.aeroncookbook.rsm;

public class RunRsm
{
    public static void main(final String[] args)
    {
        final EventListener eventListener = new EventListener();
        final SimpleStateMachine bsm = new SimpleStateMachine();
        bsm.addListener(eventListener);

        final AddCommand add = new AddCommand();
        add.value = 7;

        final MultiplyCommand multiply = new MultiplyCommand();
        multiply.value = 6;

        final SetCommand set = new SetCommand();
        set.value = 5;

        bsm.set(set);
        bsm.add(add);
        bsm.multiply(multiply);
    }


}
