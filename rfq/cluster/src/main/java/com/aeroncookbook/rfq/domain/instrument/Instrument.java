/*
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

package com.aeroncookbook.rfq.domain.instrument;

public class Instrument
{
    private final String cusip;
    private final int minSize;
    private boolean enabled;

    public Instrument(final String cusip, final boolean enabled, final int minSize)
    {
        this.cusip = cusip;
        this.enabled = enabled;
        this.minSize = minSize;
    }

    public String getCusip()
    {
        return cusip;
    }

    public int getMinSize()
    {
        return minSize;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final Instrument that = (Instrument)o;

        return cusip.equals(that.cusip);
    }

    @Override
    public int hashCode()
    {
        return cusip.hashCode();
    }
}
