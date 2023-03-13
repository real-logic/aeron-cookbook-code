/*
 * Copyright 2023 Adaptive Financial Consulting
 * Copyright 2023 Shaun Laurens
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

package com.aeroncookbook.rfq.infra;

import com.aeroncookbook.cluster.rfq.sbe.CancelRfqResult;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqResult;
import com.aeroncookbook.rfq.domain.instrument.Instrument;
import com.aeroncookbook.rfq.domain.rfq.Rfq;

import java.util.List;

/**
 * Interface for responding to auction requests, encapsulating the SBE encoding and Aeron interactions
 */
public interface ClusterClientResponder
{

    void sendInstrumentAdded(String correlation);

    void sendInstrumentEnabledFlagSet(String correlation, boolean success);

    void sendInstruments(String correlation, List<Instrument> values);

    void broadcastNewRfq(Rfq rfq);

    void createRfqConfirm(String correlation, Rfq rfq, CreateRfqResult result);

    void broadcastRfqExpired(Rfq rfq);

    void cancelRfqConfirm(String correlation, Rfq rfq, CancelRfqResult result);

    void broadcastRfqCanceled(Rfq rfq);
}
