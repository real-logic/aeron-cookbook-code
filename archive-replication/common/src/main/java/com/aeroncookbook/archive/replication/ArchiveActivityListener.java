package com.aeroncookbook.archive.replication;

import io.aeron.archive.client.ControlEventListener;
import io.aeron.archive.client.RecordingSignalConsumer;
import io.aeron.archive.codecs.ControlResponseCode;
import io.aeron.archive.codecs.RecordingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveActivityListener implements ControlEventListener, RecordingSignalConsumer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveActivityListener.class);

    @Override
    public void onResponse(long controlSessionId, long correlationId, long relevantId, ControlResponseCode code,
                           String errorMessage)
    {
        LOGGER.info("code={} error={}", code, errorMessage);
    }

    @Override
    public void onSignal(long controlSessionId, long correlationId, long recordingId, long subscriptionId,
                         long position, RecordingSignal signal)
    {
        LOGGER.info("recordingId={} position={}, signal={}", recordingId, position, signal);
    }
}
