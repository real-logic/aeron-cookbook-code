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

package com.aeroncookbook.rfq.admin.cluster;

import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.logbuffer.Header;
import com.aeroncookbook.rfq.admin.util.EnvironmentUtil;
import io.aeron.samples.cluster.protocol.AddAuctionBidCommandResultDecoder;
import io.aeron.samples.cluster.protocol.AddAuctionBidResult;
import io.aeron.samples.cluster.protocol.AddAuctionResult;
import io.aeron.samples.cluster.protocol.AddParticipantCommandResultDecoder;
import io.aeron.samples.cluster.protocol.AuctionListDecoder;
import io.aeron.samples.cluster.protocol.AuctionStatus;
import io.aeron.samples.cluster.protocol.AuctionUpdateEventDecoder;
import io.aeron.samples.cluster.protocol.CreateAuctionCommandResultDecoder;
import io.aeron.samples.cluster.protocol.MessageHeaderDecoder;
import io.aeron.samples.cluster.protocol.NewAuctionEventDecoder;
import io.aeron.samples.cluster.protocol.ParticipantListDecoder;
import org.agrona.DirectBuffer;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Admin client egress listener
 */
public class AdminClientEgressListener implements EgressListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminClientEgressListener.class);
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final AuctionUpdateEventDecoder auctionUpdateEventDecoder = new AuctionUpdateEventDecoder();
    private final AddParticipantCommandResultDecoder addParticipantDecoder = new AddParticipantCommandResultDecoder();
    private final CreateAuctionCommandResultDecoder createAuctionResultDecoder =
        new CreateAuctionCommandResultDecoder();
    private final NewAuctionEventDecoder newAuctionEventDecoder = new NewAuctionEventDecoder();
    private final AddAuctionBidCommandResultDecoder addBidResultDecoder = new AddAuctionBidCommandResultDecoder();
    private final AuctionListDecoder auctionListDecoder = new AuctionListDecoder();
    private final ParticipantListDecoder participantListDecoder = new ParticipantListDecoder();
    private final PendingMessageManager pendingMessageManager;
    private LineReader lineReader;

    /**
     * Constructor
     * @param pendingMessageManager the manager for pending messages
     */
    public AdminClientEgressListener(final PendingMessageManager pendingMessageManager)
    {
        this.pendingMessageManager = pendingMessageManager;
    }

    @Override
    public void onMessage(
        final long clusterSessionId,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        if (length < MessageHeaderDecoder.ENCODED_LENGTH)
        {
            LOGGER.warn("Message too short");
            return;
        }
        messageHeaderDecoder.wrap(buffer, offset);

        switch (messageHeaderDecoder.templateId())
        {
            case AddParticipantCommandResultDecoder.TEMPLATE_ID ->
            {
                addParticipantDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                final String correlationId = addParticipantDecoder.correlationId();
                final long addedId = addParticipantDecoder.participantId();
                log("Participant added with id " + addedId, AttributedStyle.GREEN);
                pendingMessageManager.markMessageAsReceived(correlationId);
            }
            case CreateAuctionCommandResultDecoder.TEMPLATE_ID ->
            {
                createAuctionResultDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                final long auctionId = createAuctionResultDecoder.auctionId();
                final AddAuctionResult result = createAuctionResultDecoder.result();
                final String correlationId = createAuctionResultDecoder.correlationId();
                pendingMessageManager.markMessageAsReceived(correlationId);
                if (result.equals(AddAuctionResult.SUCCESS))
                {
                    log("Auction added with id: " + auctionId,
                        AttributedStyle.GREEN);
                }
                else
                {
                    log("Add auction rejected with reason: " + result.name(), AttributedStyle.RED);
                }
            }
            case NewAuctionEventDecoder.TEMPLATE_ID ->
            {
                newAuctionEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                final long auctionId = newAuctionEventDecoder.auctionId();
                final String auctionName = newAuctionEventDecoder.name();
                log("New auction: " + "'" + auctionName + "' (" + auctionId + ")", AttributedStyle.CYAN);
            }
            case AddAuctionBidCommandResultDecoder.TEMPLATE_ID ->
            {
                addBidResultDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
                final long auctionId = addBidResultDecoder.auctionId();
                final AddAuctionBidResult result = addBidResultDecoder.result();
                final String correlationId = addBidResultDecoder.correlationId();

                pendingMessageManager.markMessageAsReceived(correlationId);
                if (result.equals(AddAuctionBidResult.SUCCESS))
                {
                    log("Bid added to auction " + auctionId, AttributedStyle.GREEN);
                }
                else
                {
                    log("Add bid rejected with reason: " + result.name(), AttributedStyle.RED);
                }
            }
            case AuctionUpdateEventDecoder.TEMPLATE_ID -> displayAuctionUpdate(buffer, offset);
            case AuctionListDecoder.TEMPLATE_ID -> displayAuctions(buffer, offset);
            case ParticipantListDecoder.TEMPLATE_ID -> displayParticipants(buffer, offset);
            default -> log("unknown message type: " + messageHeaderDecoder.templateId(), AttributedStyle.RED);
        }
    }

    private void displayAuctionUpdate(final DirectBuffer buffer, final int offset)
    {
        auctionUpdateEventDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final long auctionId = auctionUpdateEventDecoder.auctionId();
        final AuctionStatus auctionStatus = auctionUpdateEventDecoder.status();
        final int bidCount = auctionUpdateEventDecoder.bidCount();
        final long currentPrice = auctionUpdateEventDecoder.currentPrice();
        final long winningParticipantId = auctionUpdateEventDecoder.winningParticipantId();
        pendingMessageManager.markMessageAsReceived(auctionUpdateEventDecoder.correlationId());

        if (bidCount == 0)
        {
            if (auctionStatus.equals(AuctionStatus.CLOSED))
            {
                log("Auction " + auctionId + " has ended. There were no bids.", AttributedStyle.YELLOW);
            }
            else
            {
                log("Auction " + auctionId + " is now in state " +
                    auctionStatus.name() + ". There have been " +
                    auctionUpdateEventDecoder.bidCount() + " bids.", AttributedStyle.YELLOW);
            }
        }
        else
        {
            if (auctionStatus.equals(AuctionStatus.CLOSED))
            {
                final int participantId = EnvironmentUtil.tryGetParticipantId();
                if (participantId != 0 && winningParticipantId == participantId)
                {
                    log("Auction " + auctionId + " won! Total " + bidCount + " bids. Winning price was " +
                        currentPrice, AttributedStyle.GREEN);
                }
                else
                {
                    log("Auction " + auctionId + " has ended. Total " + bidCount + " bids. Winning price was " +
                        currentPrice + ", and the winning bidder is " + winningParticipantId, AttributedStyle.YELLOW);
                }
            }
            else
            {
                log("Auction update event: auction " + auctionId + " is now in state " +
                    auctionStatus.name() + ". Total " + bidCount + " bids. Current price is " +
                    currentPrice + ". The winning bidder is " + winningParticipantId,
                    AttributedStyle.YELLOW);
            }
        }
    }

    private void displayParticipants(final DirectBuffer buffer, final int offset)
    {
        participantListDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        pendingMessageManager.markMessageAsReceived(participantListDecoder.correlationId());
        final ParticipantListDecoder.ParticipantsDecoder participants = participantListDecoder.participants();
        final int count = participants.count();
        if (0 == count)
        {
            log("No participants exist in the cluster.",
                AttributedStyle.YELLOW);
        }
        else
        {
            log("Participant count: " + count, AttributedStyle.YELLOW);
            while (participants.hasNext())
            {
                participants.next();
                final long participantId = participants.participantId();
                final String name = participants.name();
                log("Participant: id " + participantId + " name: '" + name + "'", AttributedStyle.YELLOW);
            }
        }
    }

    private void displayAuctions(final DirectBuffer buffer, final int offset)
    {
        auctionListDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        pendingMessageManager.markMessageAsReceived(auctionListDecoder.correlationId());
        final AuctionListDecoder.AuctionsDecoder auction = auctionListDecoder.auctions();
        final int count = auction.count();
        if (0 == count)
        {
            log("No auctions exist in the cluster. Closed auctions are deleted automatically.",
                AttributedStyle.YELLOW);
        }
        else
        {
            log("Auction count: " + count, AttributedStyle.YELLOW);
            while (auction.hasNext())
            {
                auction.next();

                final long auctionId = auction.auctionId();
                final long createdBy = auction.createdByParticipantId();
                final long startTime = auction.startTime();
                final long endTime = auction.endTime();
                final long winningParticipantId = auction.winningParticipantId();
                final long currentPrice = auction.currentPrice();
                final AuctionStatus status = auction.status();
                final String name = auction.name();

                log("Auction '" + name + "' with id " + auctionId + " created by " + createdBy +
                    " is now in state " + status.name(), AttributedStyle.YELLOW);

                final int participantId = EnvironmentUtil.tryGetParticipantId();
                if (participantId != 0 && winningParticipantId == participantId)
                {
                    log(" Winning auction with price " +
                        currentPrice, AttributedStyle.YELLOW);
                }
                else if (winningParticipantId != -1)
                {
                    log(" Current winning participant " + winningParticipantId + " with price " +
                        currentPrice, AttributedStyle.YELLOW);
                }
            }
        }
    }

    @Override
    public void onSessionEvent(
        final long correlationId,
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final EventCode code,
        final String detail)
    {
        if (code != EventCode.OK)
        {
            log("Session event: " + code.name() + " " + detail + ". leadershipTermId=" + leadershipTermId,
                AttributedStyle.YELLOW);
        }
    }

    @Override
    public void onNewLeader(
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final String ingressEndpoints)
    {
        log("New Leader: " + leaderMemberId + ". leadershipTermId=" + leadershipTermId, AttributedStyle.YELLOW);
    }

    /**
     * Sets the terminal
     *
     * @param lineReader the lineReader
     */
    public void setLineReader(final LineReader lineReader)
    {
        this.lineReader = lineReader;
    }

    /**
     * Logs a message to the terminal if available or to the logger if not
     *
     * @param message message to log
     * @param color   message color to use
     */
    private void log(final String message, final int color)
    {
        LineReaderHelper.log(lineReader, message, color);
    }
}
