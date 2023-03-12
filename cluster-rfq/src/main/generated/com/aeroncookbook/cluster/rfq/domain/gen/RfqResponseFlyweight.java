package com.aeroncookbook.cluster.rfq.domain.gen;

import com.eider.util.IndexUpdateConsumer;
import java.lang.Integer;
import java.lang.Long;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RfqResponseFlyweight {
  /**
   * The eider spec id for this type. Useful in switch statements to detect type in first 16bits.
   */
  public static final short EIDER_ID = 25;

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
   * The byte offset in the byte array for this INT. Byte length is 4.
   */
  private static final int ID_OFFSET = 8;

  /**
   * The byte offset in the byte array for this INT. Byte length is 4.
   */
  private static final int RFQID_OFFSET = 12;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int CREATIONTIME_OFFSET = 16;

  /**
   * The byte offset in the byte array for this INT. Byte length is 4.
   */
  private static final int USER_OFFSET = 24;

  /**
   * The byte offset in the byte array for this SHORT. Byte length is 2.
   */
  private static final int RESPONSETYPE_OFFSET = 28;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int PRICE_OFFSET = 30;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int CLUSTERSESSION_OFFSET = 38;

  /**
   * The total bytes required to store the object.
   */
  public static final int BUFFER_LENGTH = 46;

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
   * The consumer notified of indexed field updates. Used to maintain indexes.
   */
  private IndexUpdateConsumer<Integer> indexUpdateNotifierRfqId = null;

  /**
   * The consumer notified of indexed field updates. Used to maintain indexes.
   */
  private IndexUpdateConsumer<Long> indexUpdateNotifierClusterSession = null;

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
   * Internal field to support the lockKey method.
   */
  private boolean keyLocked = false;

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
    keyLocked = false;
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
   * Sets the indexed field update notifier to provided consumer.
   */
  public void setIndexNotifierForRfqId(IndexUpdateConsumer<Integer> indexedNotifier) {
    this.indexUpdateNotifierRfqId = indexedNotifier;
  }

  /**
   * Sets the indexed field update notifier to provided consumer.
   */
  public void setIndexNotifierForClusterSession(IndexUpdateConsumer<Long> indexedNotifier) {
    this.indexUpdateNotifierClusterSession = indexedNotifier;
  }

  /**
   * Reads id as stored in the buffer.
   */
  public int readId() {
    return buffer.getInt(initialOffset + ID_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes id to the buffer. Returns true if success, false if not.This field is marked key=true.
   * @param value Value for the id to write to buffer.
   */
  public boolean writeId(int value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (keyLocked) throw new RuntimeException("Cannot write key after locking");
    mutableBuffer.putInt(initialOffset + ID_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Prevents any further updates to the key field.
   */
  public void lockKeyId() {
    keyLocked = true;
  }

  /**
   * Reads rfqId as stored in the buffer.
   */
  public int readRfqId() {
    return buffer.getInt(initialOffset + RFQID_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes rfqId to the buffer. Returns true if success, false if not. Indexed field.
   * @param value Value for the rfqId to write to buffer.
   */
  public boolean writeRfqId(int value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (indexUpdateNotifierRfqId != null) {
      indexUpdateNotifierRfqId.accept(initialOffset, value);
    }
    mutableBuffer.putInt(initialOffset + RFQID_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads creationTime as stored in the buffer.
   */
  public long readCreationTime() {
    return buffer.getLong(initialOffset + CREATIONTIME_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes creationTime to the buffer. Returns true if success, false if not.
   * @param value Value for the creationTime to write to buffer.
   */
  public boolean writeCreationTime(long value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putLong(initialOffset + CREATIONTIME_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads user as stored in the buffer.
   */
  public int readUser() {
    return buffer.getInt(initialOffset + USER_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes user to the buffer. Returns true if success, false if not.
   * @param value Value for the user to write to buffer.
   */
  public boolean writeUser(int value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putInt(initialOffset + USER_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads responseType as stored in the buffer.
   */
  public short readResponseType() {
    return buffer.getShort(initialOffset + RESPONSETYPE_OFFSET);
  }

  /**
   * Writes responseType to the buffer. Returns true if success, false if not.
   * @param value Value for the responseType to write to buffer.
   */
  public boolean writeResponseType(short value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putShort(initialOffset + RESPONSETYPE_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads price as stored in the buffer.
   */
  public long readPrice() {
    return buffer.getLong(initialOffset + PRICE_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes price to the buffer. Returns true if success, false if not.
   * @param value Value for the price to write to buffer.
   */
  public boolean writePrice(long value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putLong(initialOffset + PRICE_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads clusterSession as stored in the buffer.
   */
  public long readClusterSession() {
    return buffer.getLong(initialOffset + CLUSTERSESSION_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes clusterSession to the buffer. Returns true if success, false if not. Indexed field.
   * @param value Value for the clusterSession to write to buffer.
   */
  public boolean writeClusterSession(long value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (indexUpdateNotifierClusterSession != null) {
      indexUpdateNotifierClusterSession.accept(initialOffset, value);
    }
    mutableBuffer.putLong(initialOffset + CLUSTERSESSION_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * True if transactions are supported; false if not.
   */
  public boolean supportsTransactions() {
    return false;
  }
}
