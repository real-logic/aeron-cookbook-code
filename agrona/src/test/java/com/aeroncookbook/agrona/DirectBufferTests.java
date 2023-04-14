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

package com.aeroncookbook.agrona;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectBufferTests
{
    @Test
    public void unsafeLongExtras()
    {
        //allocate a buffer to store the long
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(Long.BYTES));

        //place 41 at index 0
        unsafeBuffer.putLong(0, 41);
        //add 1 to the long at index 0 and return the old value
        final long originalValue = unsafeBuffer.getAndAddLong(0, 1);
        //read the value of the long at index 0
        final long plus1 = unsafeBuffer.getLong(0);
        assertEquals(41, originalValue);
        assertEquals(42, plus1);

        //read current value while writing a new value
        final long oldValue = unsafeBuffer.getAndSetLong(0, 43);
        //read the value of the long at index 0
        final long newValue = unsafeBuffer.getLong(0);
        assertEquals(42, oldValue);
        assertEquals(43, newValue);

        //check the value was what was expected, returning true/false if it was. Then update the value a new value
        final boolean wasExpected = unsafeBuffer.compareAndSetLong(0, 43, 44);
        //read the value of the long at index 0
        final long updatedValue = unsafeBuffer.getLong(0);

        assertTrue(wasExpected);
        assertEquals(44, updatedValue);

        //check the value was what was expected, returning true/false if it was. Then update the value a new value
        final boolean notAsExpected = unsafeBuffer.compareAndSetLong(0, 502, 688);
        //read the value of the long at index 0
        final long ignoredUpdate = unsafeBuffer.getLong(0);

        assertFalse(notAsExpected);
        assertNotEquals(688, ignoredUpdate);
    }
}
