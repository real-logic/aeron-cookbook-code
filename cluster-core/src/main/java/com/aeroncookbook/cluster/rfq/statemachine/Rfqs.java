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
import com.aeroncookbook.cluster.rfq.domain.gen.RfqSequence;
import com.aeroncookbook.cluster.rfq.domain.gen.RfqsRepository;
import com.aeroncookbook.cluster.rfq.gen.AcceptRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CancelRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CounterRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.CreateRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.DisconnectRfqUserCommand;
import com.aeroncookbook.cluster.rfq.gen.QuoteRequestEvent;
import com.aeroncookbook.cluster.rfq.gen.QuoteRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RejectRfqCommand;
import com.aeroncookbook.cluster.rfq.gen.RfqErrorEvent;
import com.aeroncookbook.cluster.rfq.instrument.gen.Instrument;
import com.aeroncookbook.cluster.rfq.instruments.Instruments;
import com.aeroncookbook.cluster.rfq.statemachine.actors.Requestor;
import com.aeroncookbook.cluster.rfq.statemachine.actors.Responder;
import com.aeroncookbook.cluster.rfq.statemachine.actors.RfqActor;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqAccepted;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCanceled;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCompleted;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCountered;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqCreated;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqExpired;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqInvited;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqQuoted;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqRejected;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqState;
import com.aeroncookbook.cluster.rfq.statemachine.states.RfqStates;
import com.aeroncookbook.cluster.rfq.util.Snapshotable;
import io.aeron.ExclusivePublication;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Int2ObjectHashMap;

public class Rfqs extends Snapshotable
{
    static final String UNKNOWN_RFQ = "Unknown RFQ";
    private final Instruments instruments;
    private final ClusterProxy clusterProxy;
    private final RfqsRepository repository;
    private final RfqSequence sequence;
    private final Int2ObjectHashMap<RfqState> stateMachineStates;
    private final DirectBuffer inviteBuffer;
    private final DirectBuffer errorBuffer;
    private final QuoteRequestEvent quoteRequestEvent;
    private final RfqErrorEvent rfqErrorEvent;

    public Rfqs(Instruments instruments, ClusterProxy clusterProxy)
    {
        this.instruments = instruments;
        this.clusterProxy = clusterProxy;

        this.repository = RfqsRepository.createWithCapacity(1000);
        this.sequence = RfqSequence.INSTANCE();

        this.stateMachineStates = new Int2ObjectHashMap<>();
        buildStates(stateMachineStates);

        quoteRequestEvent = new QuoteRequestEvent();
        inviteBuffer = new ExpandableArrayBuffer(QuoteRequestEvent.BUFFER_LENGTH);
        quoteRequestEvent.setBufferWriteHeader(inviteBuffer, 0);

        rfqErrorEvent = new RfqErrorEvent();
        errorBuffer = new ExpandableArrayBuffer(RfqErrorEvent.BUFFER_LENGTH);
        rfqErrorEvent.setBufferWriteHeader(errorBuffer, 0);
    }

    public void createRfq(CreateRfqCommand createRfqCommand, long timestamp)
    {
        final int nextSequence = sequence.nextRfqIdSequence();
        final RfqFlyweight rfq = repository.appendWithKey(nextSequence);

        if (rfq == null)
        {
            replyError(-1, "System at capacity", createRfqCommand.readClOrdId());
            return;
        }

        Instrument forCusip = instruments.getForCusip(createRfqCommand.readCusip());

        if (forCusip == null)
        {
            replyError(-1, "Unknown CUSIP", createRfqCommand.readClOrdId());
            return;
        }

        if (createRfqCommand.readQuantity() < forCusip.readMinSize())
        {
            replyError(-1, "RFQ is for smaller quantity than instrument min size", createRfqCommand.readClOrdId());
            return;
        }

        rfq.writeCreationTime(timestamp);
        rfq.writeRequesterClOrdId(createRfqCommand.readClOrdId());
        rfq.writeExpiryTime(createRfqCommand.readExpireTimeMs());
        rfq.writeLimitPrice(createRfqCommand.readLimitPrice());
        rfq.writeQuantity(createRfqCommand.readQuantity());
        rfq.writeRequester(0);
        rfq.writeState(RfqStates.CREATED.getStateId());
        rfq.writeSide(createRfqCommand.readSide());
        rfq.writeRequester(createRfqCommand.readUserId());
        rfq.writeSecurityId(forCusip.readSecurityId());

        broadcastInviteToEveryoneExcept(rfq, createRfqCommand.readUserId());
    }

    public void cancelRfq(CancelRfqCommand cancelRfqCommand, long timestamp)
    {
        RfqFlyweight rfqToCancel = repository.getByKey(cancelRfqCommand.readRfqId());
        if (rfqToCancel == null)
        {
            replyError(cancelRfqCommand.readRfqId(), UNKNOWN_RFQ, "");
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToCancel, cancelRfqCommand.readUserId());
        if (actor == null)
        {
            replyError(cancelRfqCommand.readRfqId(), "Cannot cancel RFQ, no relation to user", "");
            return;
        }

        if (actor.canCancel() && rfqCanTransitionToState(rfqToCancel, RfqStates.CANCELED))
        {
            rfqToCancel.writeState(transitionTo(rfqToCancel, RfqStates.CANCELED));
            rfqToCancel.writeLastUpdate(timestamp);
            rfqToCancel.writeLastUpdateUser(cancelRfqCommand.readUserId());
        }
    }

    public void rejectRfq(RejectRfqCommand rejectRfqCommand, long timestamp)
    {
        RfqFlyweight rfqToReject = repository.getByKey(rejectRfqCommand.readRfqId());

        if (rfqToReject == null)
        {
            replyError(rejectRfqCommand.readRfqId(), UNKNOWN_RFQ, "");
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToReject, rejectRfqCommand.readUserId());

        if (actor == null)
        {
            replyError(rejectRfqCommand.readRfqId(), "Cannot reject RFQ, no relation to user", "");
            return;
        }

        if (rfqCanTransitionToState(rfqToReject, RfqStates.REJECTED))
        {
            rfqToReject.writeState(transitionTo(rfqToReject, RfqStates.REJECTED));
            rfqToReject.writeLastUpdate(timestamp);
            rfqToReject.writeLastUpdateUser(rejectRfqCommand.readUserId());

        } else
        {
            replyError(rejectRfqCommand.readRfqId(), "Illegal transition", "");
            return;
        }
    }

    public void acceptRfq(AcceptRfqCommand acceptRfqCommand, long timestamp)
    {
        RfqFlyweight rfqToAccept = repository.getByKey(acceptRfqCommand.readRfqId());

        if (rfqToAccept == null)
        {
            replyError(acceptRfqCommand.readRfqId(), UNKNOWN_RFQ, "");
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToAccept, acceptRfqCommand.readUserId());

        if (actor == null)
        {
            replyError(acceptRfqCommand.readRfqId(), "Cannot accept RFQ, no relation to user", "");
            return;
        }

    }

    public void counterRfq(CounterRfqCommand counterRfqCommand, long timestamp)
    {
        RfqFlyweight rfqToCounter = repository.getByKey(counterRfqCommand.readRfqId());

        if (rfqToCounter == null)
        {
            replyError(counterRfqCommand.readRfqId(), UNKNOWN_RFQ, "");
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToCounter, counterRfqCommand.readUserId());

        if (actor == null)
        {
            replyError(counterRfqCommand.readRfqId(), "Cannot counter RFQ, no relation to user", "");
            return;
        }

    }

    public void quoteRfq(QuoteRfqCommand quoteRfqCommand, long timestamp)
    {
        RfqFlyweight rfqToQuote = repository.getByKey(quoteRfqCommand.readRfqId());

        if (rfqToQuote == null)
        {
            replyError(quoteRfqCommand.readRfqId(), UNKNOWN_RFQ, "");
            return;
        }

        final RfqActor actor = getActorForUserThisRfq(rfqToQuote, quoteRfqCommand.readUserId());

        if (actor == null)
        {
            replyError(quoteRfqCommand.readRfqId(), "Cannot quote RFQ, no relation to user", "");
            return;
        }
    }

    public void disconnectUser(DisconnectRfqUserCommand disconnectRfqUserCommand, long timestamp)
    {
        repository.allItems().forEachRemaining(rfqFlyweight -> cancelRfqAfterDisconnect(rfqFlyweight,
            disconnectRfqUserCommand.readUserId(), timestamp));
    }

    private void cancelRfqAfterDisconnect(RfqFlyweight rfqFlyweight, int userId, long timestamp)
    {
        if ((rfqFlyweight.readRequester() == userId || rfqFlyweight.readResponder() == userId)
            && shouldBeCancelledOnDisconnect(rfqFlyweight))
        {
            rfqFlyweight.writeState(transitionTo(rfqFlyweight, RfqStates.CANCELED));
            rfqFlyweight.writeLastUpdate(timestamp);
            rfqFlyweight.writeLastUpdateUser(userId);
        }
    }

    private boolean shouldBeCancelledOnDisconnect(RfqFlyweight rfqFlyweight)
    {
        return rfqCanTransitionToState(rfqFlyweight, RfqStates.CANCELED);
    }

    private void replyError(int rfqId, String message, String clOrdId)
    {
        rfqErrorEvent.writeRfqId(rfqId);
        rfqErrorEvent.writeError(message);
        rfqErrorEvent.writeClOrdId(clOrdId);
        clusterProxy.reply(errorBuffer, 0, RfqErrorEvent.BUFFER_LENGTH);
    }

    private void broadcastInviteToEveryoneExcept(RfqFlyweight rfq, int requestorUserId)
    {
        quoteRequestEvent.writeRfqId(rfq.readId());
        quoteRequestEvent.writeBroadcastExcludeUserId(requestorUserId);
        quoteRequestEvent.writeExpireTimeMs(rfq.readExpiryTime());
        quoteRequestEvent.writeQuantity(rfq.readQuantity());
        quoteRequestEvent.writeSide(invertSide(rfq));
        quoteRequestEvent.writeSecurityId(rfq.readSecurityId());

        clusterProxy.broadcast(inviteBuffer, 0, QuoteRequestEvent.BUFFER_LENGTH);
    }


    private RfqActor getActorForUserThisRfq(RfqFlyweight rfq, int userId)
    {
        final RfqActor actor;
        if (rfq.readResponder() == userId)
        {
            actor = Responder.INSTANCE;
        } else if (rfq.readRequester() == userId)
        {
            actor = Requestor.INSTANCE;
        } else
        {
            actor = null;
        }

        return actor;
    }

    private int transitionTo(RfqFlyweight rfqToTransition, RfqStates destinationState)
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

    private boolean rfqCanTransitionToState(RfqFlyweight rfqToTransition, RfqStates destinationState)
    {
        return stateMachineStates.get(rfqToTransition.readState()).canTransitionTo(destinationState);
    }

    private void buildStates(Int2ObjectHashMap<RfqState> stateMachineStates)
    {
        stateMachineStates.put(RfqStates.CREATED.getStateId(), RfqCreated.INSTANCE);
        stateMachineStates.put(RfqStates.INVITED.getStateId(), RfqInvited.INSTANCE);
        stateMachineStates.put(RfqStates.QUOTED.getStateId(), RfqQuoted.INSTANCE);
        stateMachineStates.put(RfqStates.COUNTERED.getStateId(), RfqCountered.INSTANCE);
        stateMachineStates.put(RfqStates.ACCEPTED.getStateId(), RfqAccepted.INSTANCE);
        stateMachineStates.put(RfqStates.REJECTED.getStateId(), RfqRejected.INSTANCE);
        stateMachineStates.put(RfqStates.EXPIRED.getStateId(), RfqExpired.INSTANCE);
        stateMachineStates.put(RfqStates.CANCELED.getStateId(), RfqCanceled.INSTANCE);
        stateMachineStates.put(RfqStates.COMPLETED.getStateId(), RfqCompleted.INSTANCE);
    }

    private String invertSide(RfqFlyweight rfq)
    {
        if ("B".equalsIgnoreCase(rfq.readSide()))
        {
            return "S";
        }
        return "B";
    }

    public RfqFlyweight getRfq(int id)
    {
        return repository.getByKey(id);
    }
}
