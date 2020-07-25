package com.aeroncookbook.cluster.rfq.domain.gen;

import io.eider.util.IndexUpdateConsumer;
import java.lang.Integer;
import java.lang.Long;
import java.lang.String;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RfqFlyweight {
  /**
   * The eider spec id for this type. Useful in switch statements to detect type in first 16bits.
   */
  public static final short EIDER_ID = 23;

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
   * The byte offset in the byte array for this SHORT. Byte length is 2.
   */
  private static final int STATE_OFFSET = 12;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int CREATIONTIME_OFFSET = 14;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int EXPIRYTIME_OFFSET = 22;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int LASTUPDATE_OFFSET = 30;

  /**
   * The byte offset in the byte array for this INT. Byte length is 4.
   */
  private static final int LASTUPDATEUSER_OFFSET = 38;

  /**
   * The byte offset in the byte array for this INT. Byte length is 4.
   */
  private static final int REQUESTER_OFFSET = 42;

  /**
   * The byte offset in the byte array for this INT. Byte length is 4.
   */
  private static final int RESPONDER_OFFSET = 46;

  /**
   * The byte offset in the byte array for this INT. Byte length is 4.
   */
  private static final int SECURITYID_OFFSET = 50;

  /**
   * The byte offset in the byte array for this FIXED_STRING. Byte length is 13.
   */
  private static final int REQUESTERCLORDID_OFFSET = 54;

  /**
   * The byte offset in the byte array for this FIXED_STRING. Byte length is 11.
   */
  private static final int SIDE_OFFSET = 67;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int QUANTITY_OFFSET = 78;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int LASTPRICE_OFFSET = 86;

  /**
   * The byte offset in the byte array for this LONG. Byte length is 8.
   */
  private static final int CLUSTERSESSION_OFFSET = 94;

  /**
   * The total bytes required to store the object.
   */
  public static final int BUFFER_LENGTH = 102;

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
  private IndexUpdateConsumer<Integer> indexUpdateNotifierRequester = null;

  /**
   * The consumer notified of indexed field updates. Used to maintain indexes.
   */
  private IndexUpdateConsumer<Integer> indexUpdateNotifierResponder = null;

  /**
   * The consumer notified of indexed field updates. Used to maintain indexes.
   */
  private IndexUpdateConsumer<String> indexUpdateNotifierRequesterClOrdId = null;

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
  public void setIndexNotifierForRequester(IndexUpdateConsumer<Integer> indexedNotifier) {
    this.indexUpdateNotifierRequester = indexedNotifier;
  }

  /**
   * Sets the indexed field update notifier to provided consumer.
   */
  public void setIndexNotifierForResponder(IndexUpdateConsumer<Integer> indexedNotifier) {
    this.indexUpdateNotifierResponder = indexedNotifier;
  }

  /**
   * Sets the indexed field update notifier to provided consumer.
   */
  public void setIndexNotifierForRequesterClOrdId(IndexUpdateConsumer<String> indexedNotifier) {
    this.indexUpdateNotifierRequesterClOrdId = indexedNotifier;
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
   * Reads state as stored in the buffer.
   */
  public short readState() {
    return buffer.getShort(initialOffset + STATE_OFFSET);
  }

  /**
   * Writes state to the buffer. Returns true if success, false if not.
   * @param value Value for the state to write to buffer.
   */
  public boolean writeState(short value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putShort(initialOffset + STATE_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
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
   * Reads expiryTime as stored in the buffer.
   */
  public long readExpiryTime() {
    return buffer.getLong(initialOffset + EXPIRYTIME_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes expiryTime to the buffer. Returns true if success, false if not.
   * @param value Value for the expiryTime to write to buffer.
   */
  public boolean writeExpiryTime(long value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putLong(initialOffset + EXPIRYTIME_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads lastUpdate as stored in the buffer.
   */
  public long readLastUpdate() {
    return buffer.getLong(initialOffset + LASTUPDATE_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes lastUpdate to the buffer. Returns true if success, false if not.
   * @param value Value for the lastUpdate to write to buffer.
   */
  public boolean writeLastUpdate(long value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putLong(initialOffset + LASTUPDATE_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads lastUpdateUser as stored in the buffer.
   */
  public int readLastUpdateUser() {
    return buffer.getInt(initialOffset + LASTUPDATEUSER_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes lastUpdateUser to the buffer. Returns true if success, false if not.
   * @param value Value for the lastUpdateUser to write to buffer.
   */
  public boolean writeLastUpdateUser(int value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putInt(initialOffset + LASTUPDATEUSER_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads requester as stored in the buffer.
   */
  public int readRequester() {
    return buffer.getInt(initialOffset + REQUESTER_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes requester to the buffer. Returns true if success, false if not. Indexed field. 
   * @param value Value for the requester to write to buffer.
   */
  public boolean writeRequester(int value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (indexUpdateNotifierRequester != null) {
      indexUpdateNotifierRequester.accept(initialOffset, value);
    }
    mutableBuffer.putInt(initialOffset + REQUESTER_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads responder as stored in the buffer.
   */
  public int readResponder() {
    return buffer.getInt(initialOffset + RESPONDER_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes responder to the buffer. Returns true if success, false if not. Indexed field. 
   * @param value Value for the responder to write to buffer.
   */
  public boolean writeResponder(int value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (indexUpdateNotifierResponder != null) {
      indexUpdateNotifierResponder.accept(initialOffset, value);
    }
    mutableBuffer.putInt(initialOffset + RESPONDER_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads securityId as stored in the buffer.
   */
  public int readSecurityId() {
    return buffer.getInt(initialOffset + SECURITYID_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes securityId to the buffer. Returns true if success, false if not.
   * @param value Value for the securityId to write to buffer.
   */
  public boolean writeSecurityId(int value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putInt(initialOffset + SECURITYID_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads requesterClOrdId as stored in the buffer.
   */
  public String readRequesterClOrdId() {
    return buffer.getStringWithoutLengthAscii(initialOffset + REQUESTERCLORDID_OFFSET, 13).trim();
  }

  /**
   * Writes requesterClOrdId to the buffer. Returns true if success, false if not. Indexed field. Warning! Does not pad the string.
   * @param value Value for the requesterClOrdId to write to buffer.
   */
  public boolean writeRequesterClOrdId(String value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (value.length() > 13) throw new RuntimeException("Field requesterClOrdId is longer than maxLength=13");
    if (indexUpdateNotifierRequesterClOrdId != null) {
      indexUpdateNotifierRequesterClOrdId.accept(initialOffset, value);
    }
    mutableBuffer.putStringWithoutLengthAscii(initialOffset + REQUESTERCLORDID_OFFSET, value);
    return true;
  }

  /**
   * Writes requesterClOrdId to the buffer with padding. 
   * @param value Value for the requesterClOrdId to write to buffer.
   */
  public boolean writeRequesterClOrdIdWithPadding(String value) {
    final String padded = String.format("%13s", value);
    return writeRequesterClOrdId(padded);
  }

  /**
   * Reads side as stored in the buffer.
   */
  public String readSide() {
    return buffer.getStringWithoutLengthAscii(initialOffset + SIDE_OFFSET, 11).trim();
  }

  /**
   * Writes side to the buffer. Returns true if success, false if not.Warning! Does not pad the string.
   * @param value Value for the side to write to buffer.
   */
  public boolean writeSide(String value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    if (value.length() > 11) throw new RuntimeException("Field side is longer than maxLength=11");
    mutableBuffer.putStringWithoutLengthAscii(initialOffset + SIDE_OFFSET, value);
    return true;
  }

  /**
   * Writes side to the buffer with padding. 
   * @param value Value for the side to write to buffer.
   */
  public boolean writeSideWithPadding(String value) {
    final String padded = String.format("%11s", value);
    return writeSide(padded);
  }

  /**
   * Reads quantity as stored in the buffer.
   */
  public long readQuantity() {
    return buffer.getLong(initialOffset + QUANTITY_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes quantity to the buffer. Returns true if success, false if not.
   * @param value Value for the quantity to write to buffer.
   */
  public boolean writeQuantity(long value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putLong(initialOffset + QUANTITY_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
    return true;
  }

  /**
   * Reads lastPrice as stored in the buffer.
   */
  public long readLastPrice() {
    return buffer.getLong(initialOffset + LASTPRICE_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Writes lastPrice to the buffer. Returns true if success, false if not.
   * @param value Value for the lastPrice to write to buffer.
   */
  public boolean writeLastPrice(long value) {
    if (!isMutable) throw new RuntimeException("Cannot write to immutable buffer");
    mutableBuffer.putLong(initialOffset + LASTPRICE_OFFSET, value, java.nio.ByteOrder.LITTLE_ENDIAN);
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
