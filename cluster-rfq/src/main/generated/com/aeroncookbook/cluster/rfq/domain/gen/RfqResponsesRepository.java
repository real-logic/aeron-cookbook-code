package com.aeroncookbook.cluster.rfq.domain.gen;

import java.lang.Integer;
import java.lang.Long;
import java.lang.Override;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

public final class RfqResponsesRepository {
  /**
   * The internal MutableDirectBuffer holding capacity instances.
   */
  private final UnsafeBuffer internalBuffer;

  /**
   * For mapping the key to the offset.
   */
  private final Int2IntHashMap offsetByKey;

  /**
   * Keeps track of valid offsets.
   */
  private final IntHashSet validOffsets;

  /**
   * Used to compute CRC32 of the underlying buffer
   */
  private final CRC32 crc32 = new CRC32();

  /**
   * The current max offset used of the buffer.
   */
  private int maxUsedOffset = 0;

  /**
   * The current count of elements in the buffer.
   */
  private int currentCount = 0;

  /**
   * The maximum count of elements in the buffer.
   */
  private final int maxCapacity;

  /**
   * The iterator for unfiltered items.
   */
  private final UnfilteredIterator unfilteredIterator;

  /**
   * The length of the internal buffer.
   */
  private final int repositoryBufferLength;

  /**
   * The flyweight used by the repository.
   */
  private RfqResponseFlyweight flyweight = null;

  /**
   * The flyweight used by the repository for reads during append from buffer operations.
   */
  private RfqResponseFlyweight appendFlyweight = null;

  /**
   * Holds the index data for the rfqId field.
   */
  private Object2ObjectHashMap<Integer, IntHashSet> indexDataForRfqId = new Object2ObjectHashMap<Integer, IntHashSet>();

  /**
   * Holds the reverse index data for the rfqId field.
   */
  private Int2ObjectHashMap<Integer> reverseIndexDataForRfqId = new Int2ObjectHashMap<Integer>();

  /**
   * Holds the index data for the clusterSession field.
   */
  private Object2ObjectHashMap<Long, IntHashSet> indexDataForClusterSession = new Object2ObjectHashMap<Long, IntHashSet>();

  /**
   * Holds the reverse index data for the clusterSession field.
   */
  private Int2ObjectHashMap<Long> reverseIndexDataForClusterSession = new Int2ObjectHashMap<Long>();

  /**
   * constructor
   * @param capacity capacity to build.
   */
  private RfqResponsesRepository(int capacity) {
    flyweight = new RfqResponseFlyweight();
    appendFlyweight = new RfqResponseFlyweight();
    maxCapacity = capacity;
    repositoryBufferLength = (capacity * RfqResponseFlyweight.BUFFER_LENGTH) + capacity;
    internalBuffer = new UnsafeBuffer(java.nio.ByteBuffer.allocateDirect(repositoryBufferLength + 1));
    internalBuffer.setMemory(0, repositoryBufferLength, (byte)0);
    offsetByKey = new Int2IntHashMap(Integer.MIN_VALUE);
    validOffsets = new IntHashSet();
    unfilteredIterator = new UnfilteredIterator();
    flyweight.setIndexNotifierForRfqId(this::updateIndexForRfqId);
    flyweight.setIndexNotifierForClusterSession(this::updateIndexForClusterSession);
  }

  /**
   * Creates a respository holding at most capacity elements.
   */
  public static RfqResponsesRepository createWithCapacity(int capacity) {
    return new RfqResponsesRepository(capacity);
  }

  /**
   * Appends an element in the buffer with the provided key. Key cannot be changed. Returns null if new element could not be created or if the key already exists.
   */
  public RfqResponseFlyweight appendWithKey(int id) {
    if (currentCount >= maxCapacity) {
      return null;
    }
    if (offsetByKey.containsKey(id)) {
      return null;
    }
    flyweight.setUnderlyingBuffer(internalBuffer, maxUsedOffset);
    offsetByKey.put(id, maxUsedOffset);
    validOffsets.add(maxUsedOffset);
    flyweight.writeHeader();
    flyweight.writeId(id);
    flyweight.lockKeyId();
    currentCount += 1;
    maxUsedOffset = maxUsedOffset + RfqResponseFlyweight.BUFFER_LENGTH + 1;
    return flyweight;
  }

  /**
   * Appends an element in the buffer by copying over from source buffer. Returns null if new element could not be created or if the key already exists.
   */
  public RfqResponseFlyweight appendByCopyFromBuffer(DirectBuffer buffer, int offset) {
    if (currentCount >= maxCapacity) {
      return null;
    }
    appendFlyweight.setUnderlyingBuffer(buffer, offset);
    if (offsetByKey.containsKey(appendFlyweight.readId())) {
      return null;
    }
    flyweight.setUnderlyingBuffer(internalBuffer, maxUsedOffset);
    offsetByKey.put(appendFlyweight.readId(), maxUsedOffset);
    validOffsets.add(maxUsedOffset);
    internalBuffer.putBytes(maxUsedOffset, buffer, offset, RfqResponseFlyweight.BUFFER_LENGTH);
    flyweight.lockKeyId();
    currentCount += 1;
    updateIndexForRfqId(maxUsedOffset, appendFlyweight.readRfqId());
    updateIndexForClusterSession(maxUsedOffset, appendFlyweight.readClusterSession());
    maxUsedOffset = maxUsedOffset + RfqResponseFlyweight.BUFFER_LENGTH + 1;
    return flyweight;
  }

  /**
   * Returns true if the given key is known; false if not.
   */
  public boolean containsKey(int id) {
    return offsetByKey.containsKey(id);
  }

  /**
   * Returns the number of elements currently in the repository.
   */
  public int getCurrentCount() {
    return currentCount;
  }

  /**
   * Returns the maximum number of elements that can be stored in the repository.
   */
  public int getCapacity() {
    return maxCapacity;
  }

  /**
   * Returns the internal buffer as a byte[]. Warning! Allocates.
   */
  private byte[] dumpBuffer() {
    byte[] tmpBuffer = new byte[repositoryBufferLength];
    internalBuffer.getBytes(0, tmpBuffer);
    return tmpBuffer;
  }

  /**
   * Moves the flyweight onto the buffer segment associated with the provided key. Returns null if not found.
   */
  public RfqResponseFlyweight getByKey(int id) {
    if (offsetByKey.containsKey(id)) {
      int offset = offsetByKey.get(id);
      flyweight.setUnderlyingBuffer(internalBuffer, offset);
      flyweight.lockKeyId();
      return flyweight;
    }
    return null;
  }

  /**
   * Moves the flyweight onto the buffer segment for the provided 0-based buffer index. Returns null if not found.
   */
  public RfqResponseFlyweight getByBufferIndex(int index) {
    if ((index + 1) <= currentCount) {
      int offset = index + (index * flyweight.BUFFER_LENGTH);
      flyweight.setUnderlyingBuffer(internalBuffer, offset);
      flyweight.lockKeyId();
      return flyweight;
    }
    return null;
  }

  /**
   * Returns offset of given 0-based index, or -1 if invalid.
   */
  public int getOffsetByBufferIndex(int index) {
    if ((index + 1) <= currentCount) {
      return index + (index * flyweight.BUFFER_LENGTH);
    }
    return -1;
  }

  /**
   * Moves the flyweight onto the buffer offset, but only if it is a valid offset. Returns null if the offset is invalid.
   */
  public RfqResponseFlyweight getByBufferOffset(int offset) {
    if (validOffsets.contains(offset)) {
      flyweight.setUnderlyingBuffer(internalBuffer, offset);
      flyweight.lockKeyId();
      return flyweight;
    }
    return null;
  }

  /**
   * Returns the underlying buffer.
   */
  public DirectBuffer getUnderlyingBuffer() {
    return internalBuffer;
  }

  /**
   * Returns the CRC32 of the underlying buffer. Warning! Allocates.
   */
  public long getCrc32() {
    crc32.reset();
    crc32.update(dumpBuffer());
    return crc32.getValue();
  }

  /**
   * Returns iterator which returns all items. 
   */
  public Iterator<RfqResponseFlyweight> allItems() {
    return unfilteredIterator;
  }

  /**
   * Accepts a notification that a flyweight's indexed field has been modified
   */
  private void updateIndexForRfqId(int offset, Integer value) {
    if (reverseIndexDataForRfqId.containsKey(offset)) {
      int oldValue = reverseIndexDataForRfqId.get(offset);
      if (!reverseIndexDataForRfqId.get(offset).equals(value)) {
        indexDataForRfqId.get(oldValue).remove(offset);
      }
    }
    if (indexDataForRfqId.containsKey(value)) {
      indexDataForRfqId.get(value).add(offset);
    } else {
      final IntHashSet items = new IntHashSet();
      items.add(offset);
      indexDataForRfqId.put(value, items);
    }
    reverseIndexDataForRfqId.put(offset, value);
  }

  /**
   * Uses index to return list of offsets matching given value.
   */
  public List<Integer> getAllWithIndexRfqIdValue(Integer value) {
    List<Integer> results = new ArrayList<Integer>();
    if (indexDataForRfqId.containsKey(value)) {
      results.addAll(indexDataForRfqId.get(value));
    }
    return results;
  }

  /**
   * Accepts a notification that a flyweight's indexed field has been modified
   */
  private void updateIndexForClusterSession(int offset, Long value) {
    if (reverseIndexDataForClusterSession.containsKey(offset)) {
      long oldValue = reverseIndexDataForClusterSession.get(offset);
      if (!reverseIndexDataForClusterSession.get(offset).equals(value)) {
        indexDataForClusterSession.get(oldValue).remove(offset);
      }
    }
    if (indexDataForClusterSession.containsKey(value)) {
      indexDataForClusterSession.get(value).add(offset);
    } else {
      final IntHashSet items = new IntHashSet();
      items.add(offset);
      indexDataForClusterSession.put(value, items);
    }
    reverseIndexDataForClusterSession.put(offset, value);
  }

  /**
   * Uses index to return list of offsets matching given value.
   */
  public List<Integer> getAllWithIndexClusterSessionValue(Long value) {
    List<Integer> results = new ArrayList<Integer>();
    if (indexDataForClusterSession.containsKey(value)) {
      results.addAll(indexDataForClusterSession.get(value));
    }
    return results;
  }

  private final class UnfilteredIterator implements Iterator<RfqResponseFlyweight> {
    private RfqResponseFlyweight iteratorFlyweight = new RfqResponseFlyweight();

    private int currentOffset = 0;

    @Override
    public boolean hasNext() {
      return currentCount != 0 && (currentOffset + RfqResponseFlyweight.BUFFER_LENGTH + 1 <=maxUsedOffset);
    }

    @Override
    public RfqResponseFlyweight next() {
      if (hasNext()) {
        if (currentOffset > maxUsedOffset) {
          throw new java.util.NoSuchElementException();
        }
        iteratorFlyweight.setUnderlyingBuffer(internalBuffer, currentOffset);
        currentOffset = currentOffset + RfqResponseFlyweight.BUFFER_LENGTH + 1;
        return iteratorFlyweight;
      }
      throw new java.util.NoSuchElementException();
    }

    public UnfilteredIterator reset() {
      currentOffset = 0;
      return this;
    }
  }
}
