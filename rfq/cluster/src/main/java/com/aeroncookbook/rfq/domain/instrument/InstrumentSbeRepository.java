package com.aeroncookbook.rfq.domain.instrument;

import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordDecoder;
import com.aeroncookbook.cluster.rfq.sbe.InstrumentRecordEncoder;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32;

public final class InstrumentSbeRepository
{
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
     * The current max offset used of the buffer.
     */
    private int maxUsedOffset = 0;
    /**
     * The current count of elements in the buffer.
     */
    private int currentCount = 0;
    /**
     * The flyweight used by the repository.
     */
    private InstrumentRecordEncoder flyweightEncoder = null;
    private InstrumentRecordDecoder flyweightDecoder = null;

    /**
     * The flyweight used by the repository for reads during append from buffer operations.
     */
    private InstrumentRecordDecoder appendFlyweightDecoder = null;

    /**
     * Holds the index data for the securityId field.
     */
    private final Object2ObjectHashMap<Integer, IntHashSet> indexDataForSecurityId =
        new Object2ObjectHashMap<>();

    /**
     * Holds the reverse index data for the securityId field.
     */
    private final Int2ObjectHashMap<Integer> reverseIndexDataForSecurityId = new Int2ObjectHashMap<>();

    /**
     * Holds the index data for the cusip field.
     */
    private final Object2ObjectHashMap<String, IntHashSet> indexDataForCusip = new Object2ObjectHashMap<>();

    /**
     * Holds the reverse index data for the cusip field.
     */
    private final Int2ObjectHashMap<String> reverseIndexDataForCusip = new Int2ObjectHashMap<>();

    /**
     * Holds the index data for the enabled field.
     */
    private final Object2ObjectHashMap<Boolean, IntHashSet> indexDataForEnabled =
        new Object2ObjectHashMap<>();

    /**
     * Holds the reverse index data for the enabled field.
     */
    private final Int2ObjectHashMap<Boolean> reverseIndexDataForEnabled = new Int2ObjectHashMap<>();

    /**
     * constructor
     *
     * @param capacity capacity to build.
     */
    private InstrumentSbeRepository(final int capacity)
    {
        flyweightDecoder = new InstrumentRecordDecoder();
        flyweightEncoder = new InstrumentRecordEncoder();
        appendFlyweightDecoder = new InstrumentRecordDecoder();
        maxCapacity = capacity;
        repositoryBufferLength = (capacity * InstrumentRecordEncoder.BLOCK_LENGTH);
        internalBuffer = new UnsafeBuffer(java.nio.ByteBuffer.allocateDirect(repositoryBufferLength));
        internalBuffer.setMemory(0, repositoryBufferLength, (byte)0);
        offsetByKey = new Int2IntHashMap(Integer.MIN_VALUE);
        validOffsets = new IntHashSet();
        unfilteredIterator = new UnfilteredIterator();
    }

    /**
     * Creates a respository holding at most capacity elements.
     * @param capacity capacity to build.
     * @return a new repository.
     */
    public static InstrumentSbeRepository createWithCapacity(final int capacity)
    {
        return new InstrumentSbeRepository(capacity);
    }

    /**
     * Appends an element in the buffer with the provided key. Key cannot be changed. Returns null if new element
     * could not be created or if the key already exists.
     *
     * @param id id of the instrument.
     * @param securityId securityId of the instrument.
     * @param cusip cusip of the instrument.
     * @param enabled enabled of the instrument.
     * @param minSize minSize of the instrument.
     * @return true if added, false if not.
     */
    public boolean appendWithKey(
        final int id,
        final int securityId,
        final String cusip,
        final boolean enabled,
        final int minSize)
    {
        if (currentCount >= maxCapacity)
        {
            return false;
        }
        if (offsetByKey.containsKey(id))
        {
            return true;
        }
        flyweightEncoder.wrap(internalBuffer, maxUsedOffset);
        offsetByKey.put(id, maxUsedOffset);
        validOffsets.add(maxUsedOffset);
        flyweightEncoder.id(id);
        flyweightEncoder.securityId(securityId);
        flyweightEncoder.cusip(cusip);
        flyweightEncoder.enabled(enabled ? BooleanType.TRUE : BooleanType.FALSE);
        flyweightEncoder.minSize(minSize);
        currentCount += 1;
        updateIndexForSecurityId(maxUsedOffset, securityId);
        updateIndexForCusip(maxUsedOffset, cusip);
        updateIndexForEnabled(maxUsedOffset, enabled);
        maxUsedOffset = maxUsedOffset + InstrumentRecordEncoder.BLOCK_LENGTH + 1;
        return true;
    }

    /**
     * Returns true if the given key is known; false if not.
     *
     * @param id id of the instrument.
     * @return true if the given key is known; false if not.
     */
    public boolean containsKey(final int id)
    {
        return offsetByKey.containsKey(id);
    }

    /**
     * Returns the number of elements currently in the repository.
     * @return the number of elements currently in the repository.
     */
    public int getCurrentCount()
    {
        return currentCount;
    }

    /**
     * Returns the maximum number of elements that can be stored in the repository.
     * @return the maximum number of elements that can be stored in the repository.
     */
    public int getCapacity()
    {
        return maxCapacity;
    }

    /**
     * Moves the flyweight onto the buffer segment associated with the provided key. Returns null if not found.
     *
     * @param id id of the instrument.
     * @return a read only record.
     */
    public Instrument getByKey(final int id)
    {
        if (offsetByKey.containsKey(id))
        {
            final int offset = offsetByKey.get(id);
            flyweightDecoder.wrap(internalBuffer, offset, InstrumentRecordDecoder.BLOCK_LENGTH,
                InstrumentRecordDecoder.SCHEMA_VERSION);
            return new Instrument(flyweightDecoder.id(), flyweightDecoder.securityId(), flyweightDecoder.cusip(),
                flyweightDecoder.enabled().equals(BooleanType.TRUE), flyweightDecoder.minSize());
        }
        return null;
    }

    /**
     * Moves the flyweight onto the buffer segment for the provided 0-based buffer index. Returns null if not found.
     * @param index 0-based buffer index.
     * @return a read only record.
     */
    public Instrument getByBufferIndex(final int index)
    {
        if ((index + 1) <= currentCount)
        {
            final int offset = index + (index * InstrumentRecordDecoder.BLOCK_LENGTH);
            flyweightDecoder.wrap(internalBuffer, offset, InstrumentRecordDecoder.BLOCK_LENGTH,
                InstrumentRecordDecoder.SCHEMA_VERSION);
            return new Instrument(flyweightDecoder.id(), flyweightDecoder.securityId(), flyweightDecoder.cusip(),
                flyweightDecoder.enabled().equals(BooleanType.TRUE), flyweightDecoder.minSize());
        }
        return null;
    }

    /**
     * Returns offset of given 0-based index, or -1 if invalid.
     * @param index 0-based index.
     * @return offset of given 0-based index, or -1 if invalid.
     */
    public int getOffsetByBufferIndex(final int index)
    {
        if ((index + 1) <= currentCount)
        {
            return index + (index * InstrumentRecordDecoder.BLOCK_LENGTH);
        }
        return -1;
    }

    /**
     * Moves the flyweight onto the buffer offset, but only if it is a valid offset.
     * Returns null if the offset is invalid.
     * @param offset offset in the buffer.
     * @return a read only record.
     */
    public Instrument getByBufferOffset(final int offset)
    {
        if (validOffsets.contains(offset))
        {
            flyweightDecoder.wrap(internalBuffer, offset, InstrumentRecordDecoder.BLOCK_LENGTH,
                InstrumentRecordDecoder.SCHEMA_VERSION);
            return new Instrument(flyweightDecoder.id(), flyweightDecoder.securityId(), flyweightDecoder.cusip(),
                flyweightDecoder.enabled().equals(BooleanType.TRUE), flyweightDecoder.minSize());
        }
        return null;
    }

    /**
     * Returns the underlying buffer.
     * @return the underlying buffer.
     */
    public DirectBuffer getUnderlyingBuffer()
    {
        return internalBuffer;
    }

    /**
     * Accepts a notification that a flyweight's indexed field has been modified
     * @param offset offset of the flyweight in the buffer.
     * @param value new value of the indexed field.
     */
    private void updateIndexForSecurityId(final int offset, final Integer value)
    {
        if (reverseIndexDataForSecurityId.containsKey(offset))
        {
            final int oldValue = reverseIndexDataForSecurityId.get(offset);
            if (!reverseIndexDataForSecurityId.get(offset).equals(value))
            {
                indexDataForSecurityId.get(oldValue).remove(offset);
            }
        }
        if (indexDataForSecurityId.containsKey(value))
        {
            indexDataForSecurityId.get(value).add(offset);
        }
        else
        {
            final IntHashSet items = new IntHashSet();
            items.add(offset);
            indexDataForSecurityId.put(value, items);
        }
        reverseIndexDataForSecurityId.put(offset, value);
    }

    /**
     * Uses index to return list of offsets matching given value.
     * @param value value to match.
     * @return list of offsets matching given value.
     */
    public List<Integer> getAllWithIndexSecurityIdValue(final Integer value)
    {
        final List<Integer> results = new ArrayList<Integer>();
        if (indexDataForSecurityId.containsKey(value))
        {
            results.addAll(indexDataForSecurityId.get(value));
        }
        return results;
    }

    /**
     * Accepts a notification that a flyweight's indexed field has been modified
     * @param offset offset of the flyweight in the buffer.
     * @param value new value of the indexed field.
     */
    private void updateIndexForCusip(final int offset, final String value)
    {
        if (reverseIndexDataForCusip.containsKey(offset))
        {
            final String oldValue = reverseIndexDataForCusip.get(offset);
            if (!reverseIndexDataForCusip.get(offset).equalsIgnoreCase(value))
            {
                indexDataForCusip.get(oldValue).remove(offset);
            }
        }
        if (indexDataForCusip.containsKey(value))
        {
            indexDataForCusip.get(value).add(offset);
        }
        else
        {
            final IntHashSet items = new IntHashSet();
            items.add(offset);
            indexDataForCusip.put(value, items);
        }
        reverseIndexDataForCusip.put(offset, value);
    }

    /**
     * Uses index to return list of offsets matching given value.
     * @param value value to match.
     * @return list of offsets matching given value.
     */
    public List<Integer> getAllWithIndexCusipValue(final String value)
    {
        final List<Integer> results = new ArrayList<>();
        if (indexDataForCusip.containsKey(value))
        {
            results.addAll(indexDataForCusip.get(value));
        }
        return results;
    }

    /**
     * Accepts a notification that a flyweight's indexed field has been modified
     * @param offset offset of the flyweight in the buffer.
     * @param value new value of the indexed field.
     */
    private void updateIndexForEnabled(final int offset, final Boolean value)
    {
        if (reverseIndexDataForEnabled.containsKey(offset))
        {
            final boolean oldValue = reverseIndexDataForEnabled.get(offset);
            if (!reverseIndexDataForEnabled.get(offset).booleanValue() == value)
            {
                indexDataForEnabled.get(oldValue).remove(offset);
            }
        }
        if (indexDataForEnabled.containsKey(value))
        {
            indexDataForEnabled.get(value).add(offset);
        }
        else
        {
            final IntHashSet items = new IntHashSet();
            items.add(offset);
            indexDataForEnabled.put(value, items);
        }
        reverseIndexDataForEnabled.put(offset, value);
    }

    /**
     * Uses index to return list of offsets matching given value.
     * @param value value to match.
     * @return list of offsets matching given value.
     */
    public List<Integer> getAllWithIndexEnabledValue(final Boolean value)
    {
        final List<Integer> results = new ArrayList<>();
        if (indexDataForEnabled.containsKey(value))
        {
            results.addAll(indexDataForEnabled.get(value));
        }
        return results;
    }

    /**
     * Modifies the enabled field of the flyweight at the given offset.
     * @param offset offset of the flyweight in the buffer.
     * @param readEnabled new value of the enabled field.
     */
    public void setEnabledFlagForOffset(final Integer offset, final boolean readEnabled)
    {
        flyweightEncoder.wrap(internalBuffer, offset);
        flyweightEncoder.enabled(readEnabled ? BooleanType.TRUE : BooleanType.FALSE);
    }

    private final class UnfilteredIterator implements Iterator<InstrumentRecordDecoder>
    {
        private final InstrumentRecordDecoder iteratorFlyweight = new InstrumentRecordDecoder();

        private int currentOffset = 0;

        @Override
        public boolean hasNext()
        {
            return currentCount != 0 && (currentOffset + InstrumentRecordDecoder.BLOCK_LENGTH + 1 <= maxUsedOffset);
        }

        @Override
        public InstrumentRecordDecoder next()
        {
            if (hasNext())
            {
                if (currentOffset > maxUsedOffset)
                {
                    throw new java.util.NoSuchElementException();
                }
                iteratorFlyweight.wrap(internalBuffer, currentOffset, InstrumentRecordDecoder.BLOCK_LENGTH,
                    InstrumentRecordDecoder.SCHEMA_VERSION);
                currentOffset = currentOffset + InstrumentRecordDecoder.BLOCK_LENGTH + 1;
                return iteratorFlyweight;
            }
            throw new java.util.NoSuchElementException();
        }

        public UnfilteredIterator reset()
        {
            currentOffset = 0;
            return this;
        }
    }
}
