package com.aeroncookbook.archive.multihost;

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
    public void onResponse(final long controlSessionId, final long correlationId, final long relevantId,
        final ControlResponseCode code, final String errorMessage)
    {
        LOGGER.info("code={} error={}", code, errorMessage);
    }

    @Override
    public void onSignal(final long controlSessionId, final long correlationId, final long recordingId,
        final long subscriptionId, final long position, final RecordingSignal signal)
    {
        LOGGER.info("recordingId={} position={}, signal={}", recordingId, position, signal);
    }
}
