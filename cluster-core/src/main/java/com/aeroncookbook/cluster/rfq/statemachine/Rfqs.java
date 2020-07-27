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

package com.aeroncookbook.cluster.rfq.statemachine;

import com.aeroncookbook.cluster.rfq.domain.gen.RfqFlyweight;
import com.aeroncookbook.cluster.rfq.domain.gen.RfqResponseFlyweight;
import com.aeroncookbook.cluster.rfq.domain.gen.RfqResponseSequence;
import com.aeroncookbook.cluster.rfq.domain.gen.RfqResponsesRepository;
import com.aeroncookbook.cluster.rfq.domain.gen.RfqSequence;
import com.aeroncookbook.cluster.rfq.domain.gen.RfqsRepository;
import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CancelRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CounterRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RejectRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RfqAcceptedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqCanceledEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqCreatedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqExpiredEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqQuotedEvent;
import com.aeroncookbook.cluster.rfq.gen.RfqRejectedEvent;
import com.aeroncookbook.cluster.rfq.instrument.gen.Instrument;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.actors.Requester;
import com.aeroncookbook.cluster.rfq.statemachine.actors.Responder;
import com.aeroncookbook.cluster.rfq.statemachine.actors.RfqActor;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqAccepted;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCanceled;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCompleted;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCountered;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCreated;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqExpired;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqQuoted;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqRejected;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqState;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqStates;
import com.aeroncookbook.cluster.rfq.util.Snapshotable;
import io.aeron.ExclusivePublication;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import java.util.List;

public class Rfqs extends Snapshotable
{
    private static final String UNKNOWN_RFQ = "Unknown RFQ";
    private static final String UNKNOWN_CUSIP = "Unknown CUSIP";
    private static final String SYSTEM_AT_CAPACITY = "System at capacity";
    private static final String MIN_SIZE = "RFQ is for smaller quantity than instrument min size";
    private static final String CANNOT_CANCEL_RFQ_NO_RELATION_TO_USER = "Cannot cancel RFQ, no relation to user";
    private static final String CANNOT_REJECT_RFQ_NO_RELATION_TO_USER = "Cannot reject RFQ, no relation to user";
    private static final String ILLEGAL_TRANSITION = "Illegal transition";
    private static final String CANNOT_ACCEPT_RFQ_NO_RELATION_TO_USER = "Cannot accept RFQ, no relation to user";
    private static final String CANNOT_ACCEPT_RFQ = "Cannot accept RFQ";
    private static final String CANNOT_REJECT_RFQ = "Cannot reject RFQ";
    private static final String CANNOT_COUNTER_RFQ_NO_RELATION_TO_USER = "Cannot counter RFQ, no relation to user";
    private static final String CANNOT_QUOTE_RFQ_OTHER_USER_ALEADY_RESPONDED = "Cannot quote RFQ, RFQ already taken";

    private final Instruments instruments;
    private final ClusterProxy clusterProxy;
    private final RfqsRepository rfqsRepository;
    private final RfqResponsesRepository rfqResponsesRepository;
    private final long activityExpiryMs;
    private final RfqSequence rfqSequence;
    private final RfqResponseSequence rfqResponseSequence;
    private final Int2ObjectHashMap<RfqState> stateMachineStates;

    private final DirectBuffer bufferError;
    private final DirectBuffer bufferCanceledRfqEvent;
    private final DirectBuffer bufferCreatedRfqEvent;
    private final DirectBuffer bufferQuotedRfqEvent;
    private final DirectBuffer bufferAcceptedRfqEvent;
    private final DirectBuffer bufferRejectedRfqEvent;
    private final DirectBuffer bufferExpireRfqEvent;

    private final RfqErrorEvent rfqErrorEvent;
    private final RfqCanceledEvent rfqCanceledEvent;
    private final RfqCreatedEvent rfqCreatedEvent;
    private final RfqQuotedEvent rfqQuotedEvent;
    private final RfqAcceptedEvent rfqAcceptedEvent;
    private final RfqRejectedEvent rfqRejectedEvent;
    private final RfqExpiredEvent rfqExpiredEvent;

    public Rfqs(final Instruments instruments, final ClusterProxy clusterProxy, final int capacity,
                final long activityExpiryMs)
    {
        this.instruments = instruments;
        this.clusterProxy = clusterProxy;

        this.rfqsRepository = RfqsRepository.createWithCapacity(capacity);
        this.rfqResponsesRepository = RfqResponsesRepository.createWithCapacity(capacity * 5);
        this.activityExpiryMs = activityExpiryMs;
        this.rfqSequence = RfqSequence.INSTANCE();
        this.rfqResponseSequence = RfqResponseSequence.INSTANCE();

        this.stateMachineStates = new Int2ObjectHashMap<>();
        buildStates(stateMachineStates);

        rfqErrorEvent = new RfqErrorEvent();
        bufferError = new ExpandableArrayBuffer(RfqErrorEvent.BUFFER_LENGTH);
        rfqErrorEvent.setBufferWriteHeader(bufferError, 0);

        rfqCanceledEvent = new RfqCanceledEvent();
        bufferCanceledRfqEvent = new ExpandableArrayBuffer(RfqCanceledEvent.BUFFER_LENGTH);
        rfqCanceledEvent.setBufferWriteHeader(bufferCanceledRfqEvent, 0);

        rfqCreatedEvent = new RfqCreatedEvent();
        bufferCreatedRfqEvent = new ExpandableArrayBuffer(RfqCreatedEvent.BUFFER_LENGTH);
        rfqCreatedEvent.setBufferWriteHeader(bufferCreatedRfqEvent, 0);

        rfqQuotedEvent = new RfqQuotedEvent();
        bufferQuotedRfqEvent = new ExpandableArrayBuffer(RfqQuotedEvent.BUFFER_LENGTH);
        rfqQuotedEvent.setBufferWriteHeader(bufferQuotedRfqEvent, 0);

        rfqAcceptedEvent = new RfqAcceptedEvent();
        bufferAcceptedRfqEvent = new ExpandableArrayBuffer(RfqAcceptedEvent.BUFFER_LENGTH);
        rfqAcceptedEvent.setBufferWriteHeader(bufferAcceptedRfqEvent, 0);

        rfqRejectedEvent = new RfqRejectedEvent();
        bufferRejectedRfqEvent = new ExpandableArrayBuffer(RfqRejectedEvent.BUFFER_LENGTH);
        rfqRejectedEvent.setBufferWriteHeader(bufferRejectedRfqEvent, 0);

        rfqExpiredEvent = new RfqExpiredEvent();
        bufferExpireRfqEvent = new ExpandableArrayBuffer(RfqExpiredEvent.BUFFER_LENGTH);
        rfqExpiredEvent.setBufferWriteHeader(bufferExpireRfqEvent, 0);
    }

    public void createRfq(final CreateRfqCommand createRfqCommand, final long timestamp, final long clusterSession)
    {
        final int nextSequence = rfqSequence.nextRfqIdSequence();
        final RfqFlyweight rfq = rfqsRepository.appendWithKey(nextSequence);
        if (rfq == null)
        {
            replyError(-1, SYSTEM_AT_CAPACITY, createRfqCommand.readClOrdId(), createRfqCommand.readCorrelation());
            return;
        }

        final Instrument forCusip = instruments.getForCusip(createRfqCommand.readCusip());
        if (forCusip == null)
        {
            rfq.writeState(RfqStates.CANCELED.getStateId());
            rfq.writeLastUpdate(timestamp);
            rfq.writeLastUpdateUser(Integer.MAX_VALUE);
            replyError(-1, UNKNOWN_CUSIP, createRfqCommand.readClOrdId(), createRfqCommand.readCorrelation());
            return;
        }

        if (createRfqCommand.readQuantity() < forCusip.readMinSize())
        {
            rfq.writeState(RfqStates.CANCELED.getStateId());
            rfq.writeLastUpdate(timestamp);
            rfq.writeLastUpdateUser(Integer.MAX_VALUE);
            replyError(-1, MIN_SIZE, createRfqCommand.readClOrdId(), createRfqCommand.readCorrelation());
            return;
        }

        //store in the repository
        rfq.writeCreationTime(timestamp);
        rfq.writeRequesterClOrdId(createRfqCommand.readClOrdId());
        rfq.writeExpiryTime(createRfqCommand.readExpireTimeMs());
        rfq.writeQuantity(createRfqCommand.readQuantity());
        rfq.writeState(RfqStates.CREATED.getStateId());
        rfq.writeSide(createRfqCommand.readSide());
        rfq.writeRequester(createRfqCommand.readUserId());
        rfq.writeSecurityId(forCusip.readSecurityId());
        rfq.writeClusterSession(clusterSession);
        rfq.writeLastUpdate(timestamp);
        rfq.writeLastUpdateUser(createRfqCommand.readUserId());

        rfqCreatedEvent.writeClOrdId(createRfqCommand.readClOrdId());
        rfqCreatedEvent.writeRfqId(nextSequence);
        rfqCreatedEvent.writeCorrelation(createRfqCommand.readCorrelation());
        rfqCreatedEvent.writeRfqId(rfq.readId());
        rfqCreatedEvent.writeRfqRequesterUserId(createRfqCommand.readUserId());
        rfqCreatedEvent.writeExpireTimeMs(rfq.readExpiryTime());
        rfqCreatedEvent.writeQuantity(rfq.readQuantity());
        rfqCreatedEvent.writeSide(invertSide(rfq));
        rfqCreatedEvent.writeSecurityId(rfq.readSecurityId());

        clusterProxy.scheduleExpiry(createRfqCommand.readExpireTimeMs(), nextSequence);
        clusterProxy.broadcast(bufferCreatedRfqEvent, 0, RfqCreatedEvent.BUFFER_LENGTH);
    }

    public void cancelRfq(final CancelRfqCommand cancelRfqCommand, final long timestamp)
    {
        final RfqFlyweight rfqToCancel = rfqsRepository.getByKey(cancelRfqCommand.readRfqId());
        if (rfqToCancel == null)
        {
            replyError(cancelRfqCommand.readRfqId(), UNKNOWN_RFQ, "",
                cancelRfqCommand.readCorrelation());
            return;
        }

        //only requester can cancel
        if (rfqToCancel.readRequester() != cancelRfqCommand.readUserId())
        {
            replyError(cancelRfqCommand.readRfqId(), CANNOT_CANCEL_RFQ_NO_RELATION_TO_USER, "",
                cancelRfqCommand.readCorrelation());
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToCancel, cancelRfqCommand.readUserId());
        if (actor == null)
        {
            replyError(cancelRfqCommand.readRfqId(), CANNOT_CANCEL_RFQ_NO_RELATION_TO_USER, "",
                cancelRfqCommand.readCorrelation());
            return;
        }

        if (actor.canCancel() && rfqCanTransitionToState(rfqToCancel, RfqStates.CANCELED))
        {
            //update repository
            rfqToCancel.writeState(transitionTo(rfqToCancel, RfqStates.CANCELED));
            rfqToCancel.writeLastUpdate(timestamp);
            rfqToCancel.writeLastUpdateUser(cancelRfqCommand.readUserId());

            rfqCanceledEvent.writeCorrelation(cancelRfqCommand.readCorrelation());
            rfqCanceledEvent.writeClOrdId(rfqToCancel.readRequesterClOrdId());
            rfqCanceledEvent.writeRfqId(cancelRfqCommand.readRfqId());
            rfqCanceledEvent.writeRequesterUserId(rfqToCancel.readRequester());
            rfqCanceledEvent.writeResponderUserId(rfqToCancel.readResponder());
            clusterProxy.broadcast(bufferCanceledRfqEvent, 0, RfqCanceledEvent.BUFFER_LENGTH);
        } else
        {
            replyError(rfqToCancel.readId(), ILLEGAL_TRANSITION, "", cancelRfqCommand.readCorrelation());
        }
    }

    public void acceptRfq(final AcceptRfqCommand acceptRfqCommand, final long timestamp, final long clusterSession)
    {
        final RfqFlyweight rfqToAccept = rfqsRepository.getByKey(acceptRfqCommand.readRfqId());

        if (rfqToAccept == null)
        {
            replyError(acceptRfqCommand.readRfqId(), UNKNOWN_RFQ, "", acceptRfqCommand.readCorrelation());
            return;
        }

        if (!rfqCanTransitionToState(rfqToAccept, RfqStates.ACCEPTED))
        {
            replyError(acceptRfqCommand.readRfqId(), ILLEGAL_TRANSITION, "", acceptRfqCommand.readCorrelation());
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToAccept, acceptRfqCommand.readUserId());
        if (actor == null)
        {
            replyError(acceptRfqCommand.readRfqId(), CANNOT_ACCEPT_RFQ_NO_RELATION_TO_USER, "",
                acceptRfqCommand.readCorrelation());
            return;
        }

        //prevent accept from your own quote
        if (rfqToAccept.readLastUpdateUser() == acceptRfqCommand.readUserId())
        {
            replyError(acceptRfqCommand.readRfqId(), ILLEGAL_TRANSITION, "", acceptRfqCommand.readCorrelation());
            return;
        }

        if (!validAccept(acceptRfqCommand))
        {
            replyError(acceptRfqCommand.readRfqId(), CANNOT_ACCEPT_RFQ, "", acceptRfqCommand.readCorrelation());
            return;
        }

        if (actor.canAccept())
        {
            //append a response
            final int responseId = rfqResponseSequence.nextRfqResponseIdSequence();
            final RfqResponseFlyweight rfqResponseFlyweight = rfqResponsesRepository.appendWithKey(responseId);

            if (rfqResponseFlyweight == null)
            {
                replyError(acceptRfqCommand.readRfqId(), SYSTEM_AT_CAPACITY, "", acceptRfqCommand.readCorrelation());
                return;
            }

            rfqResponseFlyweight.writeCreationTime(timestamp);
            rfqResponseFlyweight.writeRfqId(acceptRfqCommand.readRfqId());
            rfqResponseFlyweight.writeUser(acceptRfqCommand.readUserId());
            rfqResponseFlyweight.writeClusterSession(clusterSession);
            if (actor.isRequester())
            {
                rfqResponseFlyweight.writeResponseType(RfqResponseType.REQUESTOR_ACCEPTED.getResponseTypeId());
            } else
            {
                rfqResponseFlyweight.writeResponseType(RfqResponseType.RESPONDER_ACCEPTED.getResponseTypeId());
            }

            //update the RFQ to Accepted
            rfqToAccept.writeState(transitionTo(rfqToAccept, RfqStates.ACCEPTED));
            rfqToAccept.writeLastUpdateUser(acceptRfqCommand.readUserId());
            rfqToAccept.writeLastUpdate(timestamp);

            rfqAcceptedEvent.writeCorrelation(acceptRfqCommand.readCorrelation());
            rfqAcceptedEvent.writeRequesterClOrdId(rfqToAccept.readRequesterClOrdId());
            rfqAcceptedEvent.writeAcceptedByUserId(acceptRfqCommand.readUserId());
            rfqAcceptedEvent.writeRequesterUserId(rfqToAccept.readRequester());
            rfqAcceptedEvent.writeResponderUserId(rfqToAccept.readResponder());
            rfqAcceptedEvent.writePrice(rfqToAccept.readLastPrice());
            clusterProxy.broadcast(bufferAcceptedRfqEvent, 0, RfqAcceptedEvent.BUFFER_LENGTH);
        }
    }

    public void rejectRfq(final RejectRfqCommand rejectRfqCommand, final long timestamp, final long clusterSession)
    {
        final RfqFlyweight rfqToReject = rfqsRepository.getByKey(rejectRfqCommand.readRfqId());

        if (rfqToReject == null)
        {
            replyError(rejectRfqCommand.readRfqId(), UNKNOWN_RFQ, "", rejectRfqCommand.readCorrelation());
            return;
        }

        if (!rfqCanTransitionToState(rfqToReject, RfqStates.REJECTED))
        {
            replyError(rejectRfqCommand.readRfqId(), ILLEGAL_TRANSITION, "", rejectRfqCommand.readCorrelation());
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToReject, rejectRfqCommand.readUserId());
        if (actor == null)
        {
            replyError(rejectRfqCommand.readRfqId(), CANNOT_REJECT_RFQ_NO_RELATION_TO_USER, "",
                rejectRfqCommand.readCorrelation());
            return;
        }

        //prevent accept from your own quote
        if (rfqToReject.readLastUpdateUser() == rejectRfqCommand.readUserId())
        {
            replyError(rejectRfqCommand.readRfqId(), ILLEGAL_TRANSITION, "", rejectRfqCommand.readCorrelation());
            return;
        }

        if (!validReject(rejectRfqCommand))
        {
            replyError(rejectRfqCommand.readRfqId(), CANNOT_REJECT_RFQ, "", rejectRfqCommand.readCorrelation());
            return;
        }

        if (actor.canAccept())
        {
            //append a response
            final int responseId = rfqResponseSequence.nextRfqResponseIdSequence();
            final RfqResponseFlyweight rfqResponseFlyweight = rfqResponsesRepository.appendWithKey(responseId);

            if (rfqResponseFlyweight == null)
            {
                replyError(rejectRfqCommand.readRfqId(), SYSTEM_AT_CAPACITY, "", rejectRfqCommand.readCorrelation());
                return;
            }

            rfqResponseFlyweight.writeCreationTime(timestamp);
            rfqResponseFlyweight.writeRfqId(rejectRfqCommand.readRfqId());
            rfqResponseFlyweight.writeUser(rejectRfqCommand.readUserId());
            rfqResponseFlyweight.writeClusterSession(clusterSession);
            if (actor.isRequester())
            {
                rfqResponseFlyweight.writeResponseType(RfqResponseType.REQUESTOR_REJECTED.getResponseTypeId());
            } else
            {
                rfqResponseFlyweight.writeResponseType(RfqResponseType.RESPONDER_REJECTED.getResponseTypeId());
            }

            //update the RFQ to Accepted
            rfqToReject.writeState(transitionTo(rfqToReject, RfqStates.REJECTED));
            rfqToReject.writeLastUpdateUser(rejectRfqCommand.readUserId());
            rfqToReject.writeLastUpdate(timestamp);

            rfqRejectedEvent.writeCorrelation(rejectRfqCommand.readCorrelation());
            rfqRejectedEvent.writeRequesterClOrdId(rfqToReject.readRequesterClOrdId());
            rfqRejectedEvent.writeRejectedByUserId(rejectRfqCommand.readUserId());
            rfqRejectedEvent.writeRequesterUserId(rfqToReject.readRequester());
            rfqRejectedEvent.writeResponderUserId(rfqToReject.readResponder());
            rfqRejectedEvent.writePrice(rfqToReject.readLastPrice());
            clusterProxy.broadcast(bufferRejectedRfqEvent, 0, RfqRejectedEvent.BUFFER_LENGTH);
        }

    }

    public void expire(final int rfqId)
    {
        final RfqFlyweight rfqToExpire = rfqsRepository.getByKey(rfqId);

        //the system is firing this event; no one to report to.
        if (rfqToExpire == null)
        {
            return;
        }

        if (rfqCanTransitionToState(rfqToExpire, RfqStates.EXPIRED))
        {
            rfqExpiredEvent.writeClOrdId(rfqToExpire.readRequesterClOrdId());
            rfqExpiredEvent.writeRequesterUserId(rfqToExpire.readRequester());
            rfqExpiredEvent.writeResponderUserId(rfqToExpire.readResponder());
            rfqExpiredEvent.writeRfqId(rfqToExpire.readId());
            clusterProxy.broadcast(bufferExpireRfqEvent, 0, RfqExpiredEvent.BUFFER_LENGTH);
        }
    }

    private boolean validAccept(final AcceptRfqCommand acceptRfqCommand)
    {
        //creates garbage.
        //ordered list of responses; newest is last
        final List<Integer> responses = rfqResponsesRepository.getAllWithIndexRfqIdValue(acceptRfqCommand.readRfqId());

        final RfqResponseFlyweight lastResponse =
            rfqResponsesRepository.getByBufferOffset(responses.get(responses.size() - 1));

        //no quotes
        if (lastResponse == null)
        {
            return false;
        }

        //can only respond to latest quote request id
        if (lastResponse.readId() != acceptRfqCommand.readRfqQuoteId())
        {
            return false;
        }

        //has to be different user, and the last thing done must have been a quote or a counter
        return lastResponse.readUser() != acceptRfqCommand.readUserId()
            && (lastResponse.readResponseType() == RfqResponseType.QUOTE_FROM_RESPONDER.getResponseTypeId()
            || lastResponse.readResponseType() == RfqResponseType.REQUESTOR_COUNTERED.getResponseTypeId()
            || lastResponse.readResponseType() == RfqResponseType.RESPONDER_COUNTERED.getResponseTypeId());
    }

    private boolean validReject(final RejectRfqCommand rejectRfqCommand)
    {
        //creates garbage.
        //ordered list of responses; newest is last
        final List<Integer> responses = rfqResponsesRepository.getAllWithIndexRfqIdValue(rejectRfqCommand.readRfqId());

        final RfqResponseFlyweight lastResponse =
            rfqResponsesRepository.getByBufferOffset(responses.get(responses.size() - 1));

        //no quotes
        if (lastResponse == null)
        {
            return false;
        }

        //can only respond to latest quote request id
        if (lastResponse.readId() != rejectRfqCommand.readRfqQuoteId())
        {
            return false;
        }

        //has to be different user, and the last thing done must have been a quote or a counter
        return lastResponse.readUser() != rejectRfqCommand.readUserId()
            && (lastResponse.readResponseType() == RfqResponseType.QUOTE_FROM_RESPONDER.getResponseTypeId()
            || lastResponse.readResponseType() == RfqResponseType.REQUESTOR_COUNTERED.getResponseTypeId()
            || lastResponse.readResponseType() == RfqResponseType.RESPONDER_COUNTERED.getResponseTypeId());
    }

    private boolean validCounter(final CounterRfqCommand counterRfqCommand)
    {
        //creates garbage.
        //ordered list of responses; newest is last
        final List<Integer> responses = rfqResponsesRepository.getAllWithIndexRfqIdValue(counterRfqCommand.readRfqId());

        final RfqResponseFlyweight lastResponse =
            rfqResponsesRepository.getByBufferOffset(responses.get(responses.size() - 1));

        //no quotes
        if (lastResponse == null)
        {
            return false;
        }

        //can only respond to latest quote request id
        if (lastResponse.readId() != counterRfqCommand.readRfqQuoteId())
        {
            return false;
        }

        //has to be different user, and the last thing done must have been a quote or a counter
        return lastResponse.readUser() != counterRfqCommand.readUserId()
            && (lastResponse.readResponseType() == RfqResponseType.QUOTE_FROM_RESPONDER.getResponseTypeId()
            || lastResponse.readResponseType() == RfqResponseType.REQUESTOR_COUNTERED.getResponseTypeId()
            || lastResponse.readResponseType() == RfqResponseType.RESPONDER_COUNTERED.getResponseTypeId());
    }

    public void counterRfq(final CounterRfqCommand counterRfqCommand, final long timestamp, final long clusterSession)
    {
        final RfqFlyweight rfqToCounter = rfqsRepository.getByKey(counterRfqCommand.readRfqId());

        if (rfqToCounter == null)
        {
            replyError(counterRfqCommand.readRfqId(), UNKNOWN_RFQ, "", counterRfqCommand.readCorrelation());
            return;
        }

        if (!rfqCanTransitionToState(rfqToCounter, RfqStates.COUNTERED))
        {
            replyError(counterRfqCommand.readRfqId(), ILLEGAL_TRANSITION, "", counterRfqCommand.readCorrelation());
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToCounter, counterRfqCommand.readUserId());
        if (actor == null)
        {
            replyError(counterRfqCommand.readRfqId(), CANNOT_COUNTER_RFQ_NO_RELATION_TO_USER, "",
                counterRfqCommand.readCorrelation());
            return;
        }

        //prevent accept from your own quote
        if (rfqToCounter.readLastUpdateUser() == counterRfqCommand.readUserId())
        {
            replyError(counterRfqCommand.readRfqId(), ILLEGAL_TRANSITION, "", counterRfqCommand.readCorrelation());
            return;
        }

        if (!validCounter(counterRfqCommand))
        {
            replyError(counterRfqCommand.readRfqId(), CANNOT_ACCEPT_RFQ_NO_RELATION_TO_USER, "",
                counterRfqCommand.readCorrelation());
            return;
        }

        if (actor.canCounter())
        {
            //append a response
            final int responseId = rfqResponseSequence.nextRfqResponseIdSequence();
            final RfqResponseFlyweight rfqResponseFlyweight = rfqResponsesRepository.appendWithKey(responseId);

            if (rfqResponseFlyweight == null)
            {
                replyError(counterRfqCommand.readRfqId(), SYSTEM_AT_CAPACITY, "",
                    counterRfqCommand.readCorrelation());
                return;
            }

            rfqResponseFlyweight.writeCreationTime(timestamp);
            rfqResponseFlyweight.writeRfqId(counterRfqCommand.readRfqId());
            rfqResponseFlyweight.writeUser(counterRfqCommand.readUserId());
            rfqResponseFlyweight.writeClusterSession(clusterSession);
            if (actor.isRequester())
            {
                rfqResponseFlyweight.writeResponseType(RfqResponseType.REQUESTOR_COUNTERED.getResponseTypeId());
            } else
            {
                rfqResponseFlyweight.writeResponseType(RfqResponseType.RESPONDER_COUNTERED.getResponseTypeId());
            }

            //update the RFQ to Countered
            rfqToCounter.writeState(transitionTo(rfqToCounter, RfqStates.COUNTERED));
            rfqToCounter.writeLastUpdateUser(counterRfqCommand.readUserId());
            rfqToCounter.writeLastPrice(counterRfqCommand.readPrice());
            rfqToCounter.writeLastUpdate(timestamp);

            rfqQuotedEvent.writeCorrelation(counterRfqCommand.readCorrelation());
            rfqQuotedEvent.writeRfqQuoteId(responseId);
            rfqQuotedEvent.writeRfqId(counterRfqCommand.readRfqId());
            rfqQuotedEvent.writePrice(counterRfqCommand.readPrice());
            rfqQuotedEvent.writeResponderUserId(rfqToCounter.readResponder());
            rfqQuotedEvent.writeRequesterUserId(rfqToCounter.readRequester());
            clusterProxy.scheduleExpiry(timestamp + activityExpiryMs, counterRfqCommand.readRfqId());
            clusterProxy.broadcast(bufferQuotedRfqEvent, 0, RfqQuotedEvent.BUFFER_LENGTH);
        }

    }

    public void quoteRfq(final QuoteRfqCommand quoteRfqCommand, final long timestamp, final long clusterSession)
    {
        final RfqFlyweight rfqToQuote = rfqsRepository.getByKey(quoteRfqCommand.readRfqId());
        if (rfqToQuote == null)
        {
            replyError(quoteRfqCommand.readRfqId(), UNKNOWN_RFQ, "", quoteRfqCommand.readCorrelation());
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToQuote, quoteRfqCommand.readResponderId());
        if (actor == null)
        {
            //when the first quote comes in, the RFQ is stamped to their user. Subsequent users have no relation to
            //the rfq.
            replyError(quoteRfqCommand.readRfqId(), CANNOT_QUOTE_RFQ_OTHER_USER_ALEADY_RESPONDED, "",
                quoteRfqCommand.readCorrelation());
            return;
        }

        if (actor.canQuote() && rfqCanTransitionToState(rfqToQuote, RfqStates.QUOTED))
        {
            //append a response
            final int responseId = rfqResponseSequence.nextRfqResponseIdSequence();
            final RfqResponseFlyweight rfqResponseFlyweight = rfqResponsesRepository.appendWithKey(responseId);

            if (rfqResponseFlyweight == null)
            {
                replyError(quoteRfqCommand.readRfqId(), SYSTEM_AT_CAPACITY, "",
                    quoteRfqCommand.readCorrelation());
                return;
            }

            rfqResponseFlyweight.writeCreationTime(timestamp);
            rfqResponseFlyweight.writeRfqId(quoteRfqCommand.readRfqId());
            rfqResponseFlyweight.writeUser(quoteRfqCommand.readResponderId());
            rfqResponseFlyweight.writePrice(quoteRfqCommand.readPrice());
            rfqResponseFlyweight.writeClusterSession(clusterSession);
            rfqResponseFlyweight.writeResponseType(RfqResponseType.QUOTE_FROM_RESPONDER.getResponseTypeId());

            //update the RFQ to Quoted
            rfqToQuote.writeState(transitionTo(rfqToQuote, RfqStates.QUOTED));
            rfqToQuote.writeLastUpdateUser(quoteRfqCommand.readResponderId());
            rfqToQuote.writeLastUpdate(timestamp);
            rfqToQuote.writeLastPrice(quoteRfqCommand.readPrice());
            rfqToQuote.writeResponder(quoteRfqCommand.readResponderId());

            rfqQuotedEvent.writeCorrelation(quoteRfqCommand.readCorrelation());
            rfqQuotedEvent.writePrice(quoteRfqCommand.readPrice());
            rfqQuotedEvent.writeRequesterUserId(rfqToQuote.readRequester());
            rfqQuotedEvent.writeResponderUserId(quoteRfqCommand.readResponderId());
            rfqQuotedEvent.writeRfqQuoteId(responseId);
            rfqQuotedEvent.writeRfqId(rfqToQuote.readId());
            clusterProxy.scheduleExpiry(timestamp + activityExpiryMs, quoteRfqCommand.readRfqId());
            clusterProxy.broadcast(bufferQuotedRfqEvent, 0, RfqQuotedEvent.BUFFER_LENGTH);
        } else
        {
            replyError(quoteRfqCommand.readRfqId(), "RFQ not accepting quotes at this time", "",
                quoteRfqCommand.readCorrelation());
        }
    }

    public void cancelOnClusterSessionDisconnect(final long timestamp, final long session)
    {
        rfqsRepository.allItems().forEachRemaining(rfqFlyweight -> cancelRfqAfterDisconnect(rfqFlyweight,
            session, timestamp));
    }

    private void cancelRfqAfterDisconnect(final RfqFlyweight rfqToCancel, final long session, final long timestamp)
    {
        if ((rfqToCancel.readClusterSession() == session)
            && rfqCanTransitionToState(rfqToCancel, RfqStates.CANCELED))
        {
            rfqToCancel.writeState(transitionTo(rfqToCancel, RfqStates.CANCELED));
            rfqToCancel.writeLastUpdate(timestamp);
            rfqToCancel.writeLastUpdateUser(Integer.MAX_VALUE);

            //inform user who canceled RFQ it is now canceled
            rfqCanceledEvent.writeClOrdId(rfqToCancel.readRequesterClOrdId());
            rfqCanceledEvent.writeRfqId(rfqToCancel.readId());
            rfqCanceledEvent.writeRequesterUserId(rfqToCancel.readRequester());
            rfqCanceledEvent.writeResponderUserId(rfqToCancel.readResponder());
            clusterProxy.broadcast(bufferCanceledRfqEvent, 0, RfqCanceledEvent.BUFFER_LENGTH);

        }
    }

    private void replyError(final int rfqId, final String message, final String clOrdId, final int correlation)
    {
        rfqErrorEvent.writeCorrelation(correlation);
        rfqErrorEvent.writeRfqId(rfqId);
        rfqErrorEvent.writeError(message);
        rfqErrorEvent.writeClOrdId(clOrdId);
        clusterProxy.reply(bufferError, 0, RfqErrorEvent.BUFFER_LENGTH);
    }

    private RfqActor getActorForUserThisRfq(final RfqFlyweight rfq, final int userId)
    {
        final RfqActor actor;
        if (rfq.readRequester() == userId)
        {
            actor = Requester.INSTANCE;
        } else if (rfq.readResponder() == userId || rfq.readResponder() == 0)
        {
            actor = Responder.INSTANCE;
        } else
        {
            actor = null;
        }

        return actor;
    }

    private short transitionTo(final RfqFlyweight rfqToTransition, final RfqStates destinationState)
    {
        return stateMachineStates.get(rfqToTransition.readState()).transitionTo(destinationState).getCurrentStateId();
    }

    public void snapshotTo(ExclusivePublication snapshotPublication)
    {
        //
    }

    @Override
    public void loadFromSnapshot(DirectBuffer buffer, int offset)
    {
        //
    }

    private boolean rfqCanTransitionToState(final RfqFlyweight rfqToTransition, final RfqStates destinationState)
    {
        return stateMachineStates.get(rfqToTransition.readState()).canTransitionTo(destinationState);
    }

    private void buildStates(final Int2ObjectHashMap<RfqState> stateMachineStates)
    {
        stateMachineStates.put(RfqStates.CREATED.getStateId(), RfqCreated.INSTANCE);
        stateMachineStates.put(RfqStates.QUOTED.getStateId(), RfqQuoted.INSTANCE);
        stateMachineStates.put(RfqStates.COUNTERED.getStateId(), RfqCountered.INSTANCE);
        stateMachineStates.put(RfqStates.ACCEPTED.getStateId(), RfqAccepted.INSTANCE);
        stateMachineStates.put(RfqStates.REJECTED.getStateId(), RfqRejected.INSTANCE);
        stateMachineStates.put(RfqStates.EXPIRED.getStateId(), RfqExpired.INSTANCE);
        stateMachineStates.put(RfqStates.CANCELED.getStateId(), RfqCanceled.INSTANCE);
        stateMachineStates.put(RfqStates.COMPLETED.getStateId(), RfqCompleted.INSTANCE);
    }

    private String invertSide(final RfqFlyweight rfq)
    {
        if ("B".equals(rfq.readSide()))
        {
            return "S";
        }
        return "B";
    }

}
