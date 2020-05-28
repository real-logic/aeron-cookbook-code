package com.aeroncookbook.cluster.rsm.protocol;

import org.agrona.MutableDirectBuffer;

public class AddCommand
{
    /**
     * The eider spec id for this type. Useful in switch statements to detect type from first 16bits.
     */
    public static final short EIDER_ID = 1001;

    /**
     * The total bytes required to store the object.
     */
    public static final int BUFFER_LENGTH = 12;

    /**
     * Indicates if this flyweight holds a fixed length object.
     */
    public static final boolean FIXED_LENGTH = true;

    /**
     * The offset for the header.
     */
    private static final int HEADER_OFFSET = 0;

    /**
     * The length offset. Required for segmented buffers.
     */
    private static final int LENGTH_OFFSET = 4;

    /**
     * The byte offset in the byte array for this INT. Byte length is 4.
     */
    private static final int VALUE_OFFSET = 8;

    /**
     * The internal MutableDirectBuffer.
     */
    private MutableDirectBuffer buffer;

    /**
     * The starting offset for reading and writing.
     */
    private int initialOffset;

    /**
     * Uses the provided {@link MutableDirectBuffer} from the given offset.
     *
     * @param buffer - buffer to read from and write to.
     * @param offset - offset to begin reading from/writing to in the buffer.
     */
    public void setUnderlyingBuffer(MutableDirectBuffer buffer, int offset)
    {
        this.initialOffset = offset;
        this.buffer = buffer;
        buffer.checkLimit(initialOffset + BUFFER_LENGTH);
    }

    /**
     * Returns the eider sequence.
     *
     * @return EIDER_ID.
     */
    public short eiderId()
    {
        return EIDER_ID;
    }

    /**
     * Writes the header data to the buffer.
     */
    public void writeHeader()
    {
        buffer.putShort(initialOffset + HEADER_OFFSET, EIDER_ID, java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(initialOffset + LENGTH_OFFSET, BUFFER_LENGTH, java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Validates the length and eiderSpecId in the header against the expected values. False if invalid.
     */
    public boolean validateHeader()
    {
        final short eiderId = buffer.getShort(initialOffset + HEADER_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
        final int bufferLength = buffer.getInt(initialOffset + LENGTH_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
        if (eiderId != EIDER_ID)
        {
            return false;
        }
        return bufferLength == BUFFER_LENGTH;
    }

    /**
     * Reads value as stored in the buffer.
     */
    public int readValue()
    {
        return buffer.getInt(initialOffset + VALUE_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Writes value to the buffer.
     *
     * @param value Value for the value to write to buffer
     */
    public void writeValue(int value)
    {
        buffer.putInt(initialOffset + VALUE_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * True if transactions are supported; false if not.
     */
    public boolean supportsTransactions()
    {
        return false;
    }
}
