/*
 * Copyright 2019-2020 Shaun Laurens.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aeroncookbook.archive;

import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.status.CountersReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SimplestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SimplestCase.class);
    private final String channel = "aeron:ipc";
    private final int streamCapture = 16;
    private final int streamReplay = 17;
    private final int sendCount = 10_000;

    private final IdleStrategy idleStrategy = new SleepingIdleStrategy();
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    private final File tempDir = Utils.createTempDir();
    boolean complete = false;
    private AeronArchive aeronArchive;
    private Aeron aeron;
    private ArchivingMediaDriver mediaDriver;

    public static void main(String[] args)
    {
        final SimplestCase simplestCase = new SimplestCase();
        simplestCase.setup();
        LOGGER.info("Writing");
        simplestCase.write();
        LOGGER.info("Reading");
        simplestCase.read();
        simplestCase.cleanUp();
    }

    private void cleanUp()
    {
        CloseHelper.quietClose(aeronArchive);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }

    private void read()
    {
        try (AeronArchive reader = AeronArchive.connect(new AeronArchive.Context().aeron(aeron)))
        {
            final long recordingId = findLatestRecording(reader, channel, streamCapture);
            final long position = 0L;
            final long length = Long.MAX_VALUE;

            final long sessionId = reader.startReplay(recordingId, position, length, channel, streamReplay);
            final String channelRead = ChannelUri.addSessionId(channel, (int) sessionId);

            final Subscription subscription = reader.context().aeron().addSubscription(channelRead, streamReplay);

            while (!subscription.isConnected())
            {
                idleStrategy.idle();
            }

            while (!complete)
            {
                int fragments = subscription.poll(this::archiveReader, 1);
                idleStrategy.idle(fragments);
            }
        }
    }

    private void write()
    {
        aeronArchive.startRecording(channel, streamCapture, SourceLocation.LOCAL);

        try (ExclusivePublication publication = aeron.addExclusivePublication(channel, streamCapture))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            for (int i = 0; i <= sendCount; i++)
            {
                buffer.putInt(0, i);
                while (publication.offer(buffer, 0, Integer.BYTES) < 0)
                {
                    idleStrategy.idle();
                }
            }

            final long stopPosition = publication.position();
            final CountersReader countersReader = aeron.countersReader();
            final int counterId = RecordingPos.findCounterIdBySession(countersReader, publication.sessionId());

            while (countersReader.getCounterValue(counterId) < stopPosition)
            {
                idleStrategy.idle();
            }
        }
    }

    private long findLatestRecording(final AeronArchive archive, String channel, int stream)
    {
        final MutableLong lastRecordingId = new MutableLong();

        final RecordingDescriptorConsumer consumer =
            (controlSessionId, correlationId, recordingId,
             startTimestamp, stopTimestamp, startPosition,
             stopPosition, initialTermId, segmentFileLength,
             termBufferLength, mtuLength, sessionId,
             streamId, strippedChannel, originalChannel,
             sourceIdentity) -> lastRecordingId.set(recordingId);

        final long fromRecordingId = 0L;
        final int recordCount = 100;

        final int foundCount = archive.listRecordingsForUri(fromRecordingId, recordCount, channel, stream, consumer);

        if (foundCount == 0)
        {
            throw new IllegalStateException("no recordings found");
        }

        return lastRecordingId.get();
    }

    public void archiveReader(DirectBuffer buffer, int offset, int length, Header header)
    {
        final int valueRead = buffer.getInt(offset);
        LOGGER.info("Received {}", valueRead);
        if (valueRead == sendCount)
        {
            complete = true;
        }
    }

    public void setup()
    {
        mediaDriver = ArchivingMediaDriver.launch(
            new MediaDriver.Context()
                .spiesSimulateConnection(true)
                .dirDeleteOnStart(true),
            new Archive.Context()
                .deleteArchiveOnStart(true)
                .archiveDir(tempDir)
        );

        aeron = Aeron.connect();

        aeronArchive = AeronArchive.connect(
            new AeronArchive.Context()
                .aeron(aeron)
        );
    }
}
