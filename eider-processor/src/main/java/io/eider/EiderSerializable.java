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

package io.eider;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Implementations read/write data through a flyweight interface to a org.agrona.DirectBuffer
 */
public interface EiderSerializable
{
    /**
     * Reads the direct buffer into the class variables.
     * @param buffer the buffer to read from
     * @param offset the offset to read from
     */
    void read(DirectBuffer buffer, int offset);

    /**
     * Writes the class variables into the direct buffer.
     * @param buffer the buffer to write into
     * @param offset the offset to write from
     */
    void write(MutableDirectBuffer buffer, int offset);

    /**
     * Gives the unique idenifier for this object amoungst those generated. Always the first long of the output data.
     * @return the type identifier
     */
    int eiderId();
}
