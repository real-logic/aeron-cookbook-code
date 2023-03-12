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

import com.aeroncookbook.rfq.domain.instrument.Instruments;
import com.aeroncookbook.rfq.domain.rfq.Rfqs;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import io.aeron.samples.cluster.protocol.AuctionIdSnapshotDecoder;
import io.aeron.samples.cluster.protocol.AuctionIdSnapshotEncoder;
import io.aeron.samples.cluster.protocol.AuctionSnapshotDecoder;
import io.aeron.samples.cluster.protocol.AuctionSnapshotEncoder;
import io.aeron.samples.cluster.protocol.EndOfSnapshotDecoder;
import io.aeron.samples.cluster.protocol.EndOfSnapshotEncoder;
import io.aeron.samples.cluster.protocol.MessageHeaderDecoder;
import io.aeron.samples.cluster.protocol.MessageHeaderEncoder;
import io.aeron.samples.cluster.protocol.ParticipantSnapshotDecoder;
import io.aeron.samples.cluster.protocol.ParticipantSnapshotEncoder;
import io.aeron.samples.domain.auctions.Auctions;
import io.aeron.samples.domain.participants.Participants;
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
    private final Auctions auctions;
    private final Participants participants;
    private final SessionMessageContext context;
    private IdleStrategy idleStrategy;

    private final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(1024);
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AuctionSnapshotDecoder auctionDecoder = new AuctionSnapshotDecoder();
    private final AuctionSnapshotEncoder auctionEncoder = new AuctionSnapshotEncoder();
    private final AuctionIdSnapshotEncoder auctionIdEncoder = new AuctionIdSnapshotEncoder();
    private final AuctionIdSnapshotDecoder auctionIdDecoder = new AuctionIdSnapshotDecoder();
    private final ParticipantSnapshotDecoder participantDecoder = new ParticipantSnapshotDecoder();
    private final ParticipantSnapshotEncoder participantEncoder = new ParticipantSnapshotEncoder();
    private final EndOfSnapshotEncoder endOfSnapshotEncoder = new EndOfSnapshotEncoder();

    /**
     * Constructor
     *
     * @param auctions     the auction domain model to read and write with snapshot interactions
     * @param participants the participant domain model to read and write with snapshot interactions
     * @param context      the session message context to use for snapshot interactions
     */
    public SnapshotManager(
        final Rfqs auctions,
        final Instruments participants,
        final SessionMessageContext context)
    {
        this.auctions = auctions;
        this.participants = participants;
        this.context = context;
    }

    /**
     * Called by the clustered service once a snapshot needs to be taken
     * @param snapshotPublication the publication to write snapshot data to
     */
    public void takeSnapshot(final ExclusivePublication snapshotPublication)
    {
        LOGGER.info("Starting snapshot...");
        offerParticipants(snapshotPublication);
        offerAuctions(snapshotPublication);
        offerAuctionIdGenerator(snapshotPublication);
        offerEndOfSnapshotMarker(snapshotPublication);
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
            case ParticipantSnapshotDecoder.TEMPLATE_ID ->
            {
                participantDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                participants.restoreParticipant(participantDecoder.participantId(), participantDecoder.name());
            }
            case AuctionSnapshotDecoder.TEMPLATE_ID ->
            {
                auctionDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                if (auctionDecoder.startTime() > context.getClusterTime())
                {
                    auctions.restoreAuction(auctionDecoder.auctionId(), auctionDecoder.createdByParticipantId(),
                        auctionDecoder.startTime(), auctionDecoder.startTimeTimerCorrelation(),
                        auctionDecoder.endTime(), auctionDecoder.endTimeTimerCorrelation(),
                        auctionDecoder.removalTimeTimerCorrelation(), auctionDecoder.winningParticipantId(),
                        auctionDecoder.name(), auctionDecoder.description());
                }
                else
                {
                    LOGGER.warn("Auction {} has already started; not restoring", auctionDecoder.auctionId());
                }
            }
            case AuctionIdSnapshotDecoder.TEMPLATE_ID ->
            {
                auctionIdDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                auctions.restoreAuctionId(auctionIdDecoder.lastId());
            }
            case EndOfSnapshotDecoder.TEMPLATE_ID -> snapshotFullyLoaded = true;
            default -> LOGGER.warn("Unknown snapshot message template id: {}", headerDecoder.templateId());
        }
    }

    /**
     * Offers the participants to the snapshot publication using the ParticipantSnapshotEncoder
     * @param snapshotPublication the publication to offer the snapshot data to
     */
    private void offerParticipants(final ExclusivePublication snapshotPublication)
    {
        participants.getParticipantList().forEach(participant ->
        {
            participantEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
            participantEncoder.participantId(participant.participantId());
            participantEncoder.name(participant.name());
            retryingOffer(snapshotPublication, buffer,
                headerEncoder.encodedLength() + participantEncoder.encodedLength());
        });
    }

    /**
     * Offers the auction id generator's last id to the snapshot publication using the AuctionIdSnapshotEncoder
     * @param snapshotPublication the publication to offer the snapshot data to
     */
    private void offerAuctionIdGenerator(final ExclusivePublication snapshotPublication)
    {
        auctionIdEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        auctionIdEncoder.lastId(auctions.getAuctionId());
        retryingOffer(snapshotPublication, buffer, headerEncoder.encodedLength() + auctionIdEncoder.encodedLength());
    }

    /**
     * Offers the auctions to the snapshot publication using the AuctionSnapshotEncoder
     * @param snapshotPublication the publication to offer the snapshot data to
     */
    private void offerAuctions(final ExclusivePublication snapshotPublication)
    {
        auctions.getAuctionList().forEach(auction ->
        {
            auctionEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
            auctionEncoder.auctionId(auction.getAuctionId());
            auctionEncoder.createdByParticipantId(auction.getCreatedByParticipantId());
            auctionEncoder.startTime(auction.getStartTime());
            auctionEncoder.startTimeTimerCorrelation(auction.getStartTimerCorrelationId());
            auctionEncoder.endTime(auction.getEndTime());
            auctionEncoder.endTimeTimerCorrelation(auction.getEndTimerCorrelationId());
            auctionEncoder.removalTimeTimerCorrelation(auction.getRemovalTimerCorrelationId());
            auctionEncoder.winningParticipantId(auction.getWinningParticipantId());
            auctionEncoder.name(auction.getName());
            auctionEncoder.description(auction.getDescription());
            retryingOffer(snapshotPublication, buffer,
                headerEncoder.encodedLength() + auctionEncoder.encodedLength());
        });
    }


    private void offerEndOfSnapshotMarker(final ExclusivePublication snapshotPublication)
    {
        endOfSnapshotEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        retryingOffer(snapshotPublication, buffer,
            headerEncoder.encodedLength() + endOfSnapshotEncoder.encodedLength());
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
