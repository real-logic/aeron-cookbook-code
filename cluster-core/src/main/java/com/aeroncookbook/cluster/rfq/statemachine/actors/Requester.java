/*
 * Copyright 2019-2023 Shaun Laurens.
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

package com.aeroncookbook.cluster.rfq.statemachine.actors;

public final class Requester implements RfqActor
{
    public static final Requester INSTANCE = new Requester();

    private Requester()
    {
        //nothing
    }

    @Override
    public boolean canCreate()
    {
        return true;
    }

    @Override
    public boolean canAccept()
    {
        return true;
    }

    @Override
    public boolean canReject()
    {
        return true;
    }

    @Override
    public boolean canQuote()
    {
        return false;
    }

    @Override
    public boolean canCounter()
    {
        return true;
    }

    @Override
    public boolean canExpire()
    {
        return false;
    }

    @Override
    public boolean canInvite()
    {
        return true;
    }

    @Override
    public boolean canComplete()
    {
        return false;
    }

    @Override
    public boolean canCancel()
    {
        return true;
    }

    @Override
    public boolean isResponder()
    {
        return false;
    }

    @Override
    public boolean isRequester()
    {
        return true;
    }

    @Override
    public boolean isSystem()
    {
        return false;
    }
}
