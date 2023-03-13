/*
 * Copyright 2023 Adaptive Financial Consulting
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aeroncookbook.rfq.infra;

import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderDecoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderEncoder;
import com.aeroncookbook.rfq.domain.instrument.Instruments;
import com.aeroncookbook.rfq.domain.rfq.Rfqs;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Manages the loading and writing of domain data snapshots within the cluster
 */
public class SnapshotManager implements FragmentHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotManager.class);
    private static final int RETRY_COUNT = 3;
    private boolean snapshotFullyLoaded = false;
    private final SessionMessageContext context;
    private IdleStrategy idleStrategy;

    private final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(1024);
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    /**
     * Constructor
     *
     * @param context      the session message context to use for snapshot interactions
     */
    public SnapshotManager(
        final SessionMessageContext context)
    {
        this.context = context;
    }

    /**
     * Called by the clustered service once a snapshot needs to be taken
     * @param snapshotPublication the publication to write snapshot data to
     */
    public void takeSnapshot(final ExclusivePublication snapshotPublication)
    {
        LOGGER.info("Starting snapshot...");
        LOGGER.info("Snapshot complete");
    }

    /**
     * Called by the clustered service once a snapshot has been provided by the cluster
     * @param snapshotImage the image to read snapshot data from
     */
    public void loadSnapshot(final Image snapshotImage)
    {
        LOGGER.info("Loading snapshot...");
        snapshotFullyLoaded = false;
        Objects.requireNonNull(idleStrategy, "Idle strategy must be set before loading snapshot");
        idleStrategy.reset();
        while (!snapshotImage.isEndOfStream())
        {
            idleStrategy.idle(snapshotImage.poll(this, 20));
        }

        if (!snapshotFullyLoaded)
        {
            LOGGER.warn("Snapshot load not completed; no end of snapshot marker found");
        }
        LOGGER.info("Snapshot load complete.");
    }

    /**
     * Provide an idle strategy for the snapshot load process
     * @param idleStrategy the idle strategy to use
     */
    public void setIdleStrategy(final IdleStrategy idleStrategy)
    {
        this.idleStrategy = idleStrategy;
    }

    /**
     *
     * @param buffer containing the data.
     * @param offset at which the data begins.
     * @param length of the data in bytes.
     * @param header representing the metadata for the data.
     */
    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        if (length < MessageHeaderDecoder.ENCODED_LENGTH)
        {
            return;
        }

        headerDecoder.wrap(buffer, offset);

        switch (headerDecoder.templateId())
        {

            default -> LOGGER.warn("Unknown snapshot message template id: {}", headerDecoder.templateId());
        }
    }

    /**
     * Retries the offer to the publication if it fails on back pressure or admin action.
     * Buffer is assumed to always start at offset 0
     * @param publication the publication to offer data to
     * @param buffer the buffer holding the source data
     * @param length the length to write
     */
    private void retryingOffer(final ExclusivePublication publication, final DirectBuffer buffer, final int length)
    {
        final int offset = 0;
        int retries = 0;
        do
        {
            final long result = publication.offer(buffer, offset, length);
            if (result > 0L)
            {
                return;
            }
            else if (result == Publication.ADMIN_ACTION || result == Publication.BACK_PRESSURED)
            {
                LOGGER.warn("backpressure or admin action on snapshot");
            }
            else if (result == Publication.NOT_CONNECTED || result == Publication.MAX_POSITION_EXCEEDED)
            {
                LOGGER.error("unexpected publication state on snapshot: {}", result);
                return;
            }
            idleStrategy.idle();
            retries += 1;
        }
        while (retries < RETRY_COUNT);

        LOGGER.error("failed to offer snapshot within {} retries", RETRY_COUNT);
    }
}
