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

import io.aeron.samples.domain.auctions.AddAuctionBidResult;
import io.aeron.samples.domain.auctions.AddAuctionResult;
import io.aeron.samples.domain.auctions.Auction;
import io.aeron.samples.domain.auctions.AuctionStatus;
import io.aeron.samples.domain.participants.Participant;

import java.util.List;

/**
 * Interface for responding to auction requests, encapsulating the SBE encoding and Aeron interactions
 */
public interface ClusterClientResponder
{

    /**
     * Responds to the client that an auction has been added with a result code and the auction id
     * and broadcasts the new auction to all clients
     * @param correlationId the correlation id for this request
     * @param auctionId the id of the auction
     * @param result the result code
     * @param startTime the start time of the auction
     * @param endTime the end time of the auction
     * @param name the name of the auction
     * @param description the description
     */
    void onAuctionAdded(
        String correlationId,
        long auctionId,
        AddAuctionResult result,
        long startTime,
        long endTime,
        String name,
        String description);

    /**
     * Responds to the client that an auction has not been added with a result code
     * @param correlationId the correlation id for this request
     * @param result the result code
     */
    void rejectAddAuction(String correlationId, AddAuctionResult result);

    /**
     * Responds to the client that a bid has been rejected with a result code and the auction id
     * @param correlationId the correlation id for the original request
     * @param auctionId the id of the auction provided in the original request
     * @param resultCode the result code
     */
    void rejectAddBid(String correlationId, long auctionId, AddAuctionBidResult resultCode);

    /**
     * Pushes an update to the state of an auction
     * @param correlationId the correlation id for the original request
     * @param auctionId the id of the auction
     * @param auctionStatus the status of the auction
     * @param currentPrice the current price of the auction
     * @param bidCount the number of bids
     * @param lastUpdateTime the time of the last update
     * @param winningParticipantId the id of the winning participant
     */
    void onAuctionUpdated(
        String correlationId,
        long auctionId,
        AuctionStatus auctionStatus,
        long currentPrice,
        int bidCount,
        long lastUpdateTime,
        long winningParticipantId);

    /**
     * Broadcasts an update for an auction once the state has been updated
     * @param auctionId the id of the auction
     * @param auctionStatus the status of the auction
     * @param currentPrice the current price of the auction
     * @param bidCount the number of bids
     * @param lastUpdateTime the time of the last update
     * @param winningParticipantId the id of the winning participant
     */
    void onAuctionStateUpdate(
        long auctionId,
        AuctionStatus auctionStatus,
        long currentPrice,
        int bidCount,
        long lastUpdateTime,
        long winningParticipantId);

    /**
     * Acknowledges that a participant has been added to the client using the correlation they provided
     * @param participantId the id of the participant added
     * @param correlationId the correlation id provided by the client
     */
    void acknowledgeParticipantAdded(long participantId, String correlationId);

    /**
     * Lists all auctions in the cluster
     *
     * @param auctionList   the list of auctions to return
     * @param correlationId
     */
    void returnAuctionList(List<Auction> auctionList, String correlationId);

    /**
     * Lists all participants in the cluster
     *
     * @param participantList the list of participants to return
     * @param correlationId
     */
    void returnParticipantList(List<Participant> participantList, String correlationId);
}
