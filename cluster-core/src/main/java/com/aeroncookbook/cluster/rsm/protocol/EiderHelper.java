package com.aeroncookbook.cluster.rsm.protocol;

import org.agrona.MutableDirectBuffer;

public final class EiderHelper
{
    /**
     * private constructor.
     */
    private EiderHelper()
    {
        //unused;
    }

    /**
     * Reads the Eider Id from the buffer at the offset provided.
     */
    public static short getEiderId(MutableDirectBuffer buffer, int offset)
    {
        return buffer.getShort(offset, java.nio.ByteOrder.LITTLE_ENDIAN);
    }
}
