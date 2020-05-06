/*
 *
 *  * Copyright 2019-2020 eleventy7
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * https://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.foo.sample;

import com.foo.sample.gen.SampleImplementationEider;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

public class SampleImplementationTest
{
    @Test
    public void canSerialize()
    {
        final SampleImplementation a = new SampleImplementation();
        a.foo = 23;
        a.foo2 = 42;

        final SampleImplementationEider eider = new SampleImplementationEider();

        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(100);
        //intelliJ is broken (2020.1)
        eider.write(buffer, 0);
    }
}
