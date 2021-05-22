package com.aeroncookbook.archive.multihost;

import io.aeron.archive.client.RecordingEventsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveProgressListener implements RecordingEventsListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveProgressListener.class);

    @Override
    public void onStart(long recordingId, long startPosition, int sessionId, int streamId, String channel,
                        String sourceIdentity)
    {
        LOGGER.info("recording started recordingId={} startPos={}", recordingId, startPosition);
    }

    @Override
    public void onProgress(long recordingId, long startPosition, long position)
    {
        LOGGER.info("recording activity recordingId={} startPos={} position={}", recordingId, startPosition,
            position);
    }

    @Override
    public void onStop(long recordingId, long startPosition, long stopPosition)
    {
        LOGGER.info("recording stopped recordingId={} startPos={} stopPos={}", recordingId, startPosition,
            stopPosition);
    }
}
