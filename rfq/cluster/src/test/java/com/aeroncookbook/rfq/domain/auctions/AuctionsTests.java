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

package com.aeroncookbook.rfq.domain.auctions;

import io.aeron.samples.domain.participants.Participants;
import com.aeroncookbook.rfq.infra.ClusterClientResponder;
import com.aeroncookbook.rfq.infra.SessionMessageContext;
import com.aeroncookbook.rfq.infra.TimerManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuctionsTests
{
    private final SessionMessageContext sessionMessageContext = mock(SessionMessageContext.class);
    private final ClusterClientResponder clientResponder = mock(ClusterClientResponder.class);
    private final Participants participants = mock(Participants.class);
    private final TimerManager timerManager = mock(TimerManager.class);

    @Test
    void testAuctionsCanBeAdded()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId, 1L, AddAuctionResult.SUCCESS, 1002L, 31003L,
            "name", "description");
        verify(timerManager).scheduleTimer(eq(1002L), any());
        verify(timerManager).scheduleTimer(eq(31003L), any());

        assertFalse(auctions.getAuctionList().isEmpty());
        assertEquals(1L, auctions.getAuctionList().get(0).getAuctionId());
        assertEquals(1002L, auctions.getAuctionList().get(0).getStartTime());
        assertEquals(31003L, auctions.getAuctionList().get(0).getEndTime());
        assertEquals("name", auctions.getAuctionList().get(0).getName());
        assertEquals("description", auctions.getAuctionList().get(0).getDescription());
        assertEquals(0, auctions.getAuctionList().get(0).getBidCount());
        assertEquals(0, auctions.getAuctionList().get(0).getCurrentPrice());
        assertEquals(Long.MIN_VALUE, auctions.getAuctionList().get(0).getLastUpdateTime());
        assertEquals(-1L, auctions.getAuctionList().get(0).getWinningParticipantId());
        assertEquals(AuctionStatus.PRE_OPEN, auctions.getAuctionList().get(0).getAuctionStatus());
    }

    @Test
    void testAuctionBidCanBeAdded()
    {
        final String correlationId1 = UUID.randomUUID().toString();
        final String correlationId2 = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);
        when(participants.isKnownParticipant(1001L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31004L, correlationId1, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId1, 1L, AddAuctionResult.SUCCESS, 1002L, 31004L,
            "name", "description");
        verify(timerManager).scheduleTimer(eq(1002L), any());
        verify(timerManager).scheduleTimer(eq(31004L), any());

        when(sessionMessageContext.getClusterTime()).thenReturn(31003L);
        auctions.addBid(1L, 1001L, 99L, correlationId2);

        verify(clientResponder).onAuctionUpdated(correlationId2, 1L, AuctionStatus.PRE_OPEN,
            99L, 1, 31003L, 1001L);
    }

    @Test
    void testAuctionsCanBeRehydratedAndAreSortedOnList()
    {
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);

        auctions.restoreAuction(2L, 1000L, 1003L, 1L, 31004L,
            2L, 3L, -1L, "name1", "description1");
        auctions.restoreAuction(1L, 1000L, 1002L, 4L, 31003L,
            5L, 6L, -1L, "name0", "description0");

        verifyNoInteractions(clientResponder);
        verify(timerManager).restoreTimer(eq(1L), any());
        verify(timerManager).restoreTimer(eq(2L), any());
        verify(timerManager).restoreTimer(eq(3L), any());
        verify(timerManager).restoreTimer(eq(4L), any());
        verify(timerManager).restoreTimer(eq(5L), any());
        verify(timerManager).restoreTimer(eq(6L), any());

        assertFalse(auctions.getAuctionList().isEmpty());
        assertEquals(2L, auctions.getAuctionList().size());

        assertEquals(1L, auctions.getAuctionList().get(0).getAuctionId());
        assertEquals(1000L, auctions.getAuctionList().get(0).getCreatedByParticipantId());
        assertEquals(1002L, auctions.getAuctionList().get(0).getStartTime());
        assertEquals(31003L, auctions.getAuctionList().get(0).getEndTime());
        assertEquals("name0", auctions.getAuctionList().get(0).getName());
        assertEquals("description0", auctions.getAuctionList().get(0).getDescription());

        assertEquals(2L, auctions.getAuctionList().get(1).getAuctionId());
        assertEquals(1000L, auctions.getAuctionList().get(1).getCreatedByParticipantId());
        assertEquals(1003L, auctions.getAuctionList().get(1).getStartTime());
        assertEquals(31004L, auctions.getAuctionList().get(1).getEndTime());
        assertEquals("name1", auctions.getAuctionList().get(1).getName());
        assertEquals("description1", auctions.getAuctionList().get(1).getDescription());
    }

    @Test
    void testThatParticipantMustBeKnown()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(false);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1001L, 31002L, correlationId, "name", "description");

        verify(clientResponder).rejectAddAuction(correlationId, AddAuctionResult.UNKNOWN_PARTICIPANT);
    }

    @Test
    void testThatStartTimeMustBeAfterClusterTime()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 999L, 31002L, correlationId, "name", "description");

        verify(clientResponder).rejectAddAuction(correlationId, AddAuctionResult.INVALID_START_TIME);
    }

    @Test
    void testThatEndTimeMustBeAfterStartTime()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 999L, correlationId, "name", "description");

        verify(clientResponder).rejectAddAuction(correlationId, AddAuctionResult.INVALID_END_TIME);
    }

    @Test
    void testThatNameCannotBeNull()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, null, "description");

        verify(clientResponder).rejectAddAuction(correlationId, AddAuctionResult.INVALID_NAME);
    }

    @Test
    void testThatDescriptionCannotBeNull()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, "name", null);

        verify(clientResponder).rejectAddAuction(correlationId, AddAuctionResult.INVALID_DESCRIPTION);
    }

    @Test
    void testThatNameCannotBeBlank()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, "", "description");

        verify(clientResponder).rejectAddAuction(correlationId, AddAuctionResult.INVALID_NAME);
    }

    @Test
    void testThatDescriptionCannotBeBlank()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, "name", "");

        verify(clientResponder).rejectAddAuction(correlationId, AddAuctionResult.INVALID_DESCRIPTION);
    }

    @Test
    void testAuctionRejectedIfAuctionNotOpenYet()
    {
        final String correlationId1 = UUID.randomUUID().toString();
        final String correlationId2 = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);
        when(participants.isKnownParticipant(1001L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31004L, correlationId1, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId1, 1L, AddAuctionResult.SUCCESS, 1002L, 31004L,
            "name", "description");

        auctions.addBid(1L, 1001L, 99L, correlationId2);

        verify(clientResponder).rejectAddBid(correlationId2, 1L, AddAuctionBidResult.AUCTION_NOT_OPEN);
    }

    @Test
    void testAuctionRejectedIfTimeAfterAuctionEnd()
    {
        final String correlationId1 = UUID.randomUUID().toString();
        final String correlationId2 = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);
        when(participants.isKnownParticipant(1001L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31004L, correlationId1, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId1, 1L, AddAuctionResult.SUCCESS, 1002L, 31004L,
            "name", "description");

        when(sessionMessageContext.getClusterTime()).thenReturn(31011L);
        auctions.addBid(1L, 31011L, 99L, correlationId2);

        verify(clientResponder).rejectAddBid(correlationId2, 1L, AddAuctionBidResult.AUCTION_NOT_OPEN);
    }

    @Test
    void testAuctionRejectedIfAuctionUnknown()
    {
        final String correlationId1 = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);

        auctions.addBid(42L, 1001L, 99L, correlationId1);

        verify(clientResponder).rejectAddBid(correlationId1, 42L, AddAuctionBidResult.UNKNOWN_AUCTION);
    }

    @Test
    void testAuctionRejectedIfBidderUnknown()
    {
        final String correlationId1 = UUID.randomUUID().toString();
        final String correlationId2 = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);
        when(participants.isKnownParticipant(1001L)).thenReturn(false);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31004L, correlationId1, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId1, 1L, AddAuctionResult.SUCCESS, 1002L, 31004L,
            "name", "description");

        when(sessionMessageContext.getClusterTime()).thenReturn(31003L);
        auctions.addBid(1L, 1001L, 99L, correlationId2);

        verify(clientResponder).rejectAddBid(correlationId2, 1L, AddAuctionBidResult.UNKNOWN_PARTICIPANT);
    }

    @Test
    void testAuctionRejectedIfSelfBidding()
    {
        final String correlationId1 = UUID.randomUUID().toString();
        final String correlationId2 = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31004L, correlationId1, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId1, 1L, AddAuctionResult.SUCCESS, 1002L, 31004L,
            "name", "description");

        when(sessionMessageContext.getClusterTime()).thenReturn(31003L);
        auctions.addBid(1L, 1000L, 99L, correlationId2);

        verify(clientResponder).rejectAddBid(correlationId2, 1L, AddAuctionBidResult.CANNOT_SELF_BID);
    }

    @Test
    void testAuctionRejectedIfNoPriceImprovement()
    {
        final String correlationId1 = UUID.randomUUID().toString();
        final String correlationId2 = UUID.randomUUID().toString();
        final String correlationId3 = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);
        when(participants.isKnownParticipant(1001L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31005L, correlationId1, "name", "description");

        //add first bid at 99L
        verify(clientResponder).onAuctionAdded(correlationId1, 1L, AddAuctionResult.SUCCESS, 1002L, 31005L,
            "name", "description");

        when(sessionMessageContext.getClusterTime()).thenReturn(31003L);
        auctions.addBid(1L, 1001L, 99L, correlationId2);

        verify(clientResponder).onAuctionUpdated(correlationId2, 1L, AuctionStatus.PRE_OPEN, 99L,
            1, 31003L, 1001L);

        //add a second bid at 90L
        when(sessionMessageContext.getClusterTime()).thenReturn(31004L);
        auctions.addBid(1L, 1001L, 90L, correlationId3);

        verify(clientResponder).rejectAddBid(correlationId3, 1L, AddAuctionBidResult.INVALID_PRICE);
    }

    @Test
    void testAuctionMovesThroughStatesCorrectly()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId, 1L, AddAuctionResult.SUCCESS, 1002L, 31003L,
            "name", "description");
        verify(timerManager).scheduleTimer(eq(1002L), any());
        verify(timerManager).scheduleTimer(eq(31003L), any());

        assertFalse(auctions.getAuctionList().isEmpty());
        assertEquals(1L, auctions.getAuctionList().get(0).getAuctionId());
        assertEquals(1002L, auctions.getAuctionList().get(0).getStartTime());
        assertEquals(31003L, auctions.getAuctionList().get(0).getEndTime());
        assertEquals(AuctionStatus.PRE_OPEN, auctions.getAuctionList().get(0).getAuctionStatus());

        when(sessionMessageContext.getClusterTime()).thenReturn(31002L);
        auctions.openAuction(1L);

        assertEquals(1, auctions.getAuctionList().size());
        assertEquals(AuctionStatus.OPEN, auctions.getAuctionList().get(0).getAuctionStatus());


        when(sessionMessageContext.getClusterTime()).thenReturn(31003L);
        auctions.closeAuction(1L);

        assertEquals(1, auctions.getAuctionList().size());
        assertEquals(AuctionStatus.CLOSED, auctions.getAuctionList().get(0).getAuctionStatus());
    }

    @Test
    void testAuctionDoesNotMoveStateIfNotTimeToOpenYet()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId, 1L, AddAuctionResult.SUCCESS, 1002L, 31003L,
            "name", "description");
        verify(timerManager).scheduleTimer(eq(1002L), any());
        verify(timerManager).scheduleTimer(eq(31003L), any());

        assertFalse(auctions.getAuctionList().isEmpty());
        assertEquals(1L, auctions.getAuctionList().get(0).getAuctionId());
        assertEquals(1002L, auctions.getAuctionList().get(0).getStartTime());
        assertEquals(31003L, auctions.getAuctionList().get(0).getEndTime());
        assertEquals(AuctionStatus.PRE_OPEN, auctions.getAuctionList().get(0).getAuctionStatus());

        when(sessionMessageContext.getClusterTime()).thenReturn(1001L);

        auctions.openAuction(1L);

        assertEquals(1, auctions.getAuctionList().size());
        assertEquals(AuctionStatus.PRE_OPEN, auctions.getAuctionList().get(0).getAuctionStatus());


        auctions.closeAuction(1L);

        assertEquals(1, auctions.getAuctionList().size());
        assertEquals(AuctionStatus.PRE_OPEN, auctions.getAuctionList().get(0).getAuctionStatus());
    }

    @Test
    void testAuctionDoesNotMoveStateIfNotLegal()
    {
        final String correlationId = UUID.randomUUID().toString();
        when(sessionMessageContext.getClusterTime()).thenReturn(1000L);
        when(participants.isKnownParticipant(1000L)).thenReturn(true);

        final Auctions auctions =
            new Auctions(sessionMessageContext, participants, clientResponder, timerManager);
        auctions.addAuction(1000L, 1002L, 31003L, correlationId, "name", "description");

        verify(clientResponder).onAuctionAdded(correlationId, 1L, AddAuctionResult.SUCCESS, 1002L, 31003L,
            "name", "description");
        verify(timerManager).scheduleTimer(eq(1002L), any());
        verify(timerManager).scheduleTimer(eq(31003L), any());

        assertFalse(auctions.getAuctionList().isEmpty());
        assertEquals(1L, auctions.getAuctionList().get(0).getAuctionId());
        assertEquals(1002L, auctions.getAuctionList().get(0).getStartTime());
        assertEquals(31003L, auctions.getAuctionList().get(0).getEndTime());
        assertEquals(AuctionStatus.PRE_OPEN, auctions.getAuctionList().get(0).getAuctionStatus());

        when(sessionMessageContext.getClusterTime()).thenReturn(31003L);

        auctions.closeAuction(1L);

        assertEquals(1, auctions.getAuctionList().size());
        assertEquals(AuctionStatus.PRE_OPEN, auctions.getAuctionList().get(0).getAuctionStatus());
    }
}
