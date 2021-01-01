/*
 * Copyright 2019-2021 Shaun Laurens.
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

package com.aeroncookbook.cluster.rfq.domain;

import io.eider.annotation.EiderAttribute;
import io.eider.annotation.EiderRepository;
import io.eider.annotation.EiderSpec;

@EiderRepository(name = "RfqsRepository")
@EiderSpec(name = "RfqFlyweight")
public class Rfq
{
    @EiderAttribute(key = true)
    private int id;
    private short state;
    private long creationTime;
    private long expiryTime;
    private long lastUpdate;
    private int lastUpdateUser;
    @EiderAttribute(indexed = true)
    private int requester;
    @EiderAttribute(indexed = true)
    private int responder;
    private int securityId;
    private int requesterCorrelationId;
    private short side;
    private long quantity;
    private long lastPrice;
    @EiderAttribute(indexed = true)
    private long clusterSession;
}
