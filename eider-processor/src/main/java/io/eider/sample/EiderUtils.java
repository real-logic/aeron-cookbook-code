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

package io.eider.sample;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.nio.ByteOrder;

public class EiderUtils
{
    public static void writeHeader(MutableDirectBuffer buffer, int offset, int eiderId)
    {
        buffer.putInt(offset, eiderId, ByteOrder.LITTLE_ENDIAN);
    }

    public static int readHeader(DirectBuffer buffer, int offset)
    {
        return buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    }
}
