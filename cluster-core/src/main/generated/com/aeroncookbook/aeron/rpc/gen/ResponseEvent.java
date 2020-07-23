package com.aeroncookbook.aeron.rpc.gen;

import java.lang.String;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ResponseEvent {
  /**
   * The eider spec id for this type. Useful in switch statements to detect type in first 16bits.
   */
  public static final short EIDER_ID = 26;

  /**
   * The eider group id for this type. Useful in switch statements to detect group in second 16bits.
   */
  public static final short EIDER_GROUP_ID = 1;

  /**
   * The offset for the EIDER_ID within the buffer.
   */
  private static final int HEADER_OFFSET = 0;

  /**
   * The offset for the EIDER_GROUP_IP within the buffer.
   */
  private static final int HEADER_GROUP_OFFSET = 2;

  /**
   * The length offset. Required for segmented buffers.
   */
  private static final int LENGTH_OFFSET = 4;

  /**
   * The byte offset in the byte array for this FIXED_STRING. Byte length is 36.
   */
  private static final int CORRELATION_OFFSET = 8;

  /**
   * The byte offset in the byte array for this FIXED_STRING. Byte length is 50.
   */
  private static final int RESULT_OFFSET = 44;

  /**
   * The total bytes required to store the object.
   */
  public static final int BUFFER_LENGTH = 94;

  /**
   * Indicates if this flyweight holds a fixed length object.
   */
  public static final boolean FIXED_LENGTH = true;

  /**
   * The internal DirectBuffer.
   */
  private DirectBuffer buffer = null;

  /**
   * The internal DirectBuffer used for mutatation opertions. Valid only if a mutable buffer was provided.
   */
  private MutableDirectBuffer mutableBuffer = null;

  /**
   * The internal UnsafeBuffer. Valid only if an unsafe buffer was provided.
   */
  private UnsafeBuffer unsafeBuffer = null;

  /**
   * The starting offset for reading and writing.
   */
  private int initialOffset;

  /**
   * Flag indicating if the buffer is mutable.
   */
  private boolean isMutable = false;

  /**
   * Flag indicating if the buffer is an UnsafeBuffer.
   */
  private boolean isUnsafe = false;

  /**
   * Uses the provided {@link org.agrona.DirectBuffer} from the given offset.
   * @param buffer - buffer to read from and write to.
   * @param offset - offset to begin reading from/writing to in the buffer.
   */
  public void setUnderlyingBuffer(DirectBuffer buffer, int offset) {
    this.initialOffset = offset;
    this.buffer = buffer;
    if (buffer instanceof UnsafeBuffer) {
      unsafeBuffer = (UnsafeBuffer) buffer;
      mutableBuffer = (MutableDirectBuffer) buffer;
      isUnsafe = true;
      isMutable = true;
    }
    else if (buffer instanceof MutableDirectBuffer) {
      mutableBuffer = (MutableDirectBuffer) buffer;
      isUnsafe = false;
      isMutable = true;
    }
    else {
      isUnsafe = false;
      isMutable = false;
    }
    buffer.checkLimit(initialOffset + BUFFER_LENGTH);
  }

  /**
   * Uses the provided {@link org.agrona.DirectBuffer} from the given offset.
   * @param buffer - buffer to read from and write to.
   * @param offset - offset to begin reading from/writing to in the buffer.
   */
  public void setBufferWriteHeader(DirectBuffer buffer, int offset) {
    setUnderlyingBuffer(buffer, offset);
    writeHeader();
  }

  /**
   * Returns the eider sequence.
   * @return EIDER_ID.
   */
  public short eiderId() {
    return EIDER_ID;
  }

  /**
   * Writes the header data to the buffer.
   */
  public void writeHeader() {
    if (!isMutable) throw new RuntimeException("cannot write to immutable buffer");
    mutableBuffer.putShort(initialOffset + HEADER_OFFSET, EIDER_ID, java.nio.ByteOrder.LITTLE_ENDIAN);
    mutableBuffer.putShort(initialOffset + HEADER_GROUP_OFFSET, EIDER_GROUP_ID, java.nio.ByteOrder.LITTLE_ENDIAN);
    mutableBuffer.putInt(initialOffset + LENGTH_OFFSET, BUFFER_LENGTH, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Validates the length and eiderSpecId in the header against the expected values. False if invalid.
   */
  public boolean validateHeader() {
    final short eiderId = buffer.getShort(initialOffset + HEADER_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
    final short eiderGroupId = buffer.getShort(initialOffset + HEADER_GROUP_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
    final int bufferLength = buffer.getInt(initialOffset + LENGTH_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
    if (eiderId != EIDER_ID) return false;
    if (eiderGroupId != EIDER_GROUP_ID) return false;
    return bufferLength == BUFFER_LENGTH;
  }

  /**
   * Reads correlation as stored in the buffer.
   */
  public String readCorrelation() {
    return buffer.getStringWithoutLengthAscii(initialOffset + CORRELATION_OFFSET, 36).trim();
  }

  /**
   * Writes correlation to the buffer. Returns true if success, false if not.Warning! Does not pad the string.
   * @param value Value for the correlation to write to buffer.
   */
  public boolean writeCorrelation(String value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (value.length() > 36) throw new RuntimeException("Field correlation is longer than maxLength=36");
    mutableBuffer.putStringWithoutLengthAscii(initialOffset + CORRELATION_OFFSET, value);
    return true;
  }

  /**
   * Writes correlation to the buffer with padding. 
   * @param value Value for the correlation to write to buffer.
   */
  public boolean writeCorrelationWithPadding(String value) {
    final String padded = String.format("%36s", value);
    return writeCorrelation(padded);
  }

  /**
   * Reads result as stored in the buffer.
   */
  public String readResult() {
    return buffer.getStringWithoutLengthAscii(initialOffset + RESULT_OFFSET, 50).trim();
  }

  /**
   * Writes result to the buffer. Returns true if success, false if not.Warning! Does not pad the string.
   * @param value Value for the result to write to buffer.
   */
  public boolean writeResult(String value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (value.length() > 50) throw new RuntimeException("Field result is longer than maxLength=50");
    mutableBuffer.putStringWithoutLengthAscii(initialOffset + RESULT_OFFSET, value);
    return true;
  }

  /**
   * Writes result to the buffer with padding. 
   * @param value Value for the result to write to buffer.
   */
  public boolean writeResultWithPadding(String value) {
    final String padded = String.format("%50s", value);
    return writeResult(padded);
  }

  /**
   * True if transactions are supported; false if not.
   */
  public boolean supportsTransactions() {
    return false;
  }
}
