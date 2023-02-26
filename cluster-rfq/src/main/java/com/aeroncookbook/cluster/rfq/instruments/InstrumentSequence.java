package com.aeroncookbook.cluster.rfq.instruments;

import org.agrona.concurrent.UnsafeBuffer;

public final class InstrumentSequence
{
    private UnsafeBuffer unsafeBuffer = null;

    private InstrumentSequence(final UnsafeBuffer buffer)
    {
        unsafeBuffer = buffer;
    }

    public static InstrumentSequence getInstance()
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(java.nio.ByteBuffer.allocateDirect(Integer.BYTES));
        final InstrumentSequence instance = new InstrumentSequence(buffer);
        instance.initializeInstrumentId(1);
        return instance;
    }

    /**
     * Reads instrumentId as stored in the buffer.
     *
     * @return The value of instrumentId.
     */
    public int readInstrumentId()
    {
        return unsafeBuffer.getInt(0, java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Increments and returns the sequence in field instrumentId.
     *
     * @return The next value for instrumentId.
     */
    public int nextInstrumentIdSequence()
    {
        return unsafeBuffer.getAndAddInt(0, 1);
    }

    /**
     * Initializes instrumentId to the provided value.
     *
     * @param value Value for the instrumentId to write to buffer.
     */
    public void initializeInstrumentId(final int value)
    {
        unsafeBuffer.putInt(0, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    }

}
