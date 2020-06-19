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

package com.aeroncookbook.cluster.rfq.statemachine;

import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.util.Snapshotable;
import io.aeron.ExclusivePublication;
import io.aeron.cluster.service.ClientSession;
import org.agrona.DirectBuffer;

public class Rfqs extends Snapshotable
{
    private final Instruments instruments;

    public Rfqs(Instruments instruments)
    {
        this.instruments = instruments;
    }

    public void createRfq(CreateRfqCommand createRfqCommand, long timestamp, ClientSession session)
    {
        //
    }

    public void snapshotTo(ExclusivePublication snapshotPublication)
    {
        //
    }

    @Override
    public void loadFromSnapshot(DirectBuffer buffer, int offset)
    {
        //
    }
}
