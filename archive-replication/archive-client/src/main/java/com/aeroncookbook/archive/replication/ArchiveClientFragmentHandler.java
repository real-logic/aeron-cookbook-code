package com.aeroncookbook.archive.replication;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveClientFragmentHandler implements FragmentHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveClientFragmentHandler.class);
    private long lastValue;
    private long lastPosition;

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header)
    {
        lastValue = buffer.getLong(offset);
        lastPosition = header.position();
        LOGGER.info("received {}", lastValue);
    }

    public long getLastValue()
    {
        return lastValue;
    }

    public long getLastPosition()
    {
        return lastPosition;
    }
}
