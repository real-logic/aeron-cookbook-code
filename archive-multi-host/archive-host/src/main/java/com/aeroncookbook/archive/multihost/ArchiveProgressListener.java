package com.aeroncookbook.archive.multihost;

import io.aeron.archive.client.RecordingEventsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveProgressListener implements RecordingEventsListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveProgressListener.class);

    @Override
    public void onStart(final long recordingId, final long startPosition, final int sessionId, final int streamId,
        final String channel, final String sourceIdentity)
    {
        LOGGER.info("recording started recordingId={} startPos={}", recordingId, startPosition);
    }

    @Override
    public void onProgress(final long recordingId, final long startPosition, final long position)
    {
        LOGGER.info("recording activity recordingId={} startPos={} position={}", recordingId, startPosition,
            position);
    }

    @Override
    public void onStop(final long recordingId, final long startPosition, final long stopPosition)
    {
        LOGGER.info("recording stopped recordingId={} startPos={} stopPos={}", recordingId, startPosition,
            stopPosition);
    }
}
