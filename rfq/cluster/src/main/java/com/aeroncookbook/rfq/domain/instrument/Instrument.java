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

        final Instrument that = (Instrument) o;

        return cusip.equals(that.cusip);
    }

    @Override
    public int hashCode()
    {
        return cusip.hashCode();
    }
}
