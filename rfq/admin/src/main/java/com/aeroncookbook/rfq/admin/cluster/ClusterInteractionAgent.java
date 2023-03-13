/*
 * Copyright 2023 Adaptive Financial Consulting
 * Copyright 2023 Shaun Laurens
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

import com.aeroncookbook.cluster.rfq.sbe.AddInstrumentEncoder;
import com.aeroncookbook.cluster.rfq.sbe.BooleanType;
import com.aeroncookbook.cluster.rfq.sbe.CancelRfqCommandEncoder;
import com.aeroncookbook.cluster.rfq.sbe.CreateRfqCommandEncoder;
import com.aeroncookbook.cluster.rfq.sbe.ListInstrumentsCommandEncoder;
import com.aeroncookbook.cluster.rfq.sbe.MessageHeaderEncoder;
import com.aeroncookbook.cluster.rfq.sbe.QuoteRfqCommandEncoder;
import com.aeroncookbook.cluster.rfq.sbe.SetInstrumentEnabledFlagEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.AddInstrumentDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.CancelRfqCommandDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.ConnectClusterDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.CreateRfqCommandDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.DisconnectClusterDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.ListInstrumentsCommandDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.QuoteRfqCommandDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.SetInstrumentEnabledFlagDecoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.Side;
import io.aeron.Publication;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.samples.cluster.ClusterConfig;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedStyle;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent to interact with the cluster
 */
public class ClusterInteractionAgent implements Agent, MessageHandler
{
    private static final long HEARTBEAT_INTERVAL = 250;
    private static final long RETRY_COUNT = 10;
    private static final String INGRESS_CHANNEL = "aeron:udp?term-length=64k";
    private final MutableDirectBuffer sendBuffer = new ExpandableDirectByteBuffer(1024);
    private final OneToOneRingBuffer adminClusterComms;
    private final IdleStrategy idleStrategy;
    private final AtomicBoolean runningFlag;
    private final PendingMessageManager pendingMessageManager;
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ConnectClusterDecoder connectClusterDecoder = new ConnectClusterDecoder();
    private final AddInstrumentDecoder addInstrumentDecoder = new AddInstrumentDecoder();
    private final CreateRfqCommandDecoder createRfqCommandDecoder = new CreateRfqCommandDecoder();
    private final SetInstrumentEnabledFlagDecoder setInstrumentEnabledDecoder = new SetInstrumentEnabledFlagDecoder();
    private final CancelRfqCommandDecoder cancelRfqCommandDecoder = new CancelRfqCommandDecoder();
    private final QuoteRfqCommandDecoder quoteRfqCommandDecoder = new QuoteRfqCommandDecoder();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final AddInstrumentEncoder addInstrumentEncoder = new AddInstrumentEncoder();
    private final ListInstrumentsCommandEncoder listInstrumentsCommandEncoder = new ListInstrumentsCommandEncoder();
    private final SetInstrumentEnabledFlagEncoder setInstrumentEnabledEncoder = new SetInstrumentEnabledFlagEncoder();
    private final CreateRfqCommandEncoder createRfqCommandEncoder = new CreateRfqCommandEncoder();
    private final CancelRfqCommandEncoder cancelRfqCommandEncoder = new CancelRfqCommandEncoder();
    private final QuoteRfqCommandEncoder quoteRfqCommandEncoder = new QuoteRfqCommandEncoder();
    private long lastHeartbeatTime = Long.MIN_VALUE;
    private AdminClientEgressListener adminClientEgressListener;
    private AeronCluster aeronCluster;
    private ConnectionState connectionState = ConnectionState.NOT_CONNECTED;
    private LineReader lineReader;
    private MediaDriver mediaDriver;

    /**
     * Creates a new agent to interact with the cluster
     *
     * @param adminClusterChannel the channel to send messages to the cluster from the REPL
     * @param idleStrategy        the idle strategy to use
     * @param runningFlag         the flag to indicate if the REPL is still running
     */
    public ClusterInteractionAgent(
        final OneToOneRingBuffer adminClusterChannel,
        final IdleStrategy idleStrategy,
        final AtomicBoolean runningFlag)
    {
        this.adminClusterComms = adminClusterChannel;
        this.idleStrategy = idleStrategy;
        this.runningFlag = runningFlag;
        this.pendingMessageManager = new PendingMessageManager(SystemEpochClock.INSTANCE);
    }

    @Override
    public int doWork()
    {
        //send cluster heartbeat roughly every 250ms
        final long now = SystemEpochClock.INSTANCE.time();
        if (now >= (lastHeartbeatTime + HEARTBEAT_INTERVAL))
        {
            lastHeartbeatTime = now;
            if (connectionState == ConnectionState.CONNECTED)
            {
                aeronCluster.sendKeepAlive();
            }
        }

        //poll inbound to this agent messages (from the REPL)
        adminClusterComms.read(this);

        //poll outbound messages from the cluster
        if (null != aeronCluster && !aeronCluster.isClosed())
        {
            aeronCluster.pollEgress();
        }

        //check for timed-out messages
        pendingMessageManager.doWork();

        //always sleep
        return 0;
    }

    @Override
    public String roleName()
    {
        return "cluster-interaction-agent";
    }

    @Override
    public void onMessage(final int msgTypeId, final MutableDirectBuffer buffer, final int offset, final int length)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        switch (messageHeaderDecoder.templateId())
        {
            case CancelRfqCommandEncoder.TEMPLATE_ID -> processCancelRfqCommand(messageHeaderDecoder, buffer, offset);
            case CreateRfqCommandDecoder.TEMPLATE_ID -> processCreateRfqCommand(messageHeaderDecoder, buffer, offset);
            case QuoteRfqCommandDecoder.TEMPLATE_ID -> processQuoteRfqCommand(messageHeaderDecoder, buffer, offset);
            case ConnectClusterDecoder.TEMPLATE_ID -> processConnectCluster(buffer, offset);
            case DisconnectClusterDecoder.TEMPLATE_ID -> processDisconnectCluster();
            case ListInstrumentsCommandDecoder.TEMPLATE_ID -> processInstrumentListCommand();
            case AddInstrumentDecoder.TEMPLATE_ID -> processAddInstrument(messageHeaderDecoder, buffer, offset);
            case SetInstrumentEnabledFlagDecoder.TEMPLATE_ID ->
                processSetInstrumentEnabled(messageHeaderDecoder, buffer, offset);
            default -> log("Unknown message type: " + messageHeaderDecoder.templateId(), AttributedStyle.RED);
        }
    }

    private void processQuoteRfqCommand(
        final MessageHeaderDecoder messageHeaderDecoder,
        final MutableDirectBuffer buffer,
        final int offset)
    {
        final String correlationId = UUID.randomUUID().toString();
        quoteRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final int rfqId = quoteRfqCommandDecoder.rfqId();
        final int responderId = quoteRfqCommandDecoder.responderId();
        final long price = quoteRfqCommandDecoder.price();

        quoteRfqCommandEncoder.wrapAndApplyHeader(sendBuffer, 0, messageHeaderEncoder);
        quoteRfqCommandEncoder.correlation(correlationId);
        quoteRfqCommandEncoder.rfqId(rfqId);
        quoteRfqCommandEncoder.responderUserId(responderId);
        quoteRfqCommandEncoder.price(price);

        retryingClusterOffer(sendBuffer, MessageHeaderEncoder.ENCODED_LENGTH +
            quoteRfqCommandEncoder.encodedLength());

        pendingMessageManager.addMessage(correlationId, "quote-rfq");
    }

    private void processCancelRfqCommand(
        final MessageHeaderDecoder messageHeaderDecoder,
        final MutableDirectBuffer buffer,
        final int offset)
    {
        final String correlationId = UUID.randomUUID().toString();
        cancelRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final int rfqId = cancelRfqCommandDecoder.rfqId();
        final int userId = cancelRfqCommandDecoder.userId();

        cancelRfqCommandEncoder.wrapAndApplyHeader(sendBuffer, 0, messageHeaderEncoder);
        cancelRfqCommandEncoder.correlation(correlationId);
        cancelRfqCommandEncoder.rfqId(rfqId);
        cancelRfqCommandEncoder.cancelUserId(userId);


        retryingClusterOffer(sendBuffer, MessageHeaderEncoder.ENCODED_LENGTH +
            cancelRfqCommandEncoder.encodedLength());

        pendingMessageManager.addMessage(correlationId, "cancel-rfq");

    }

    private void processCreateRfqCommand(
        final MessageHeaderDecoder messageHeaderDecoder,
        final MutableDirectBuffer buffer,
        final int offset)
    {
        final String correlationId = UUID.randomUUID().toString();
        createRfqCommandDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        final long expireTimeMs = createRfqCommandDecoder.expireTimeMs();
        final int quantity = createRfqCommandDecoder.quantity();
        final Side side = createRfqCommandDecoder.requesterSide();
        final String cusip = createRfqCommandDecoder.cusip();
        final int userId = createRfqCommandDecoder.requesterUserId();

        createRfqCommandEncoder.wrapAndApplyHeader(sendBuffer, 0, messageHeaderEncoder);
        createRfqCommandEncoder.correlation(correlationId);
        createRfqCommandEncoder.expireTimeMs(expireTimeMs);
        createRfqCommandEncoder.quantity(quantity);
        createRfqCommandEncoder.requesterSide(mapSide(side));
        createRfqCommandEncoder.cusip(cusip);
        createRfqCommandEncoder.requesterUserId(userId);

        retryingClusterOffer(sendBuffer, MessageHeaderEncoder.ENCODED_LENGTH +
            createRfqCommandEncoder.encodedLength());

        pendingMessageManager.addMessage(correlationId, "create-rfq");
    }

    private com.aeroncookbook.cluster.rfq.sbe.Side mapSide(final Side side)
    {
        switch (side)
        {
            case BUY ->
            {
                return com.aeroncookbook.cluster.rfq.sbe.Side.BUY;
            }
            case SELL ->
            {
                return com.aeroncookbook.cluster.rfq.sbe.Side.SELL;
            }
            default ->
            {
                throw new IllegalArgumentException("Unknown side: " + side);
            }
        }
    }

    private void processInstrumentListCommand()
    {
        final String correlationId = UUID.randomUUID().toString();

        listInstrumentsCommandEncoder.wrapAndApplyHeader(sendBuffer, 0, messageHeaderEncoder);
        listInstrumentsCommandEncoder.correlation(correlationId);

        retryingClusterOffer(sendBuffer, MessageHeaderEncoder.ENCODED_LENGTH +
            listInstrumentsCommandEncoder.encodedLength());

        pendingMessageManager.addMessage(correlationId, "instrument-list");
    }

    /**
     * Opens the cluster connection
     *
     * @param buffer the buffer containing the message
     * @param offset the offset of the message
     */
    private void processConnectCluster(final MutableDirectBuffer buffer, final int offset)
    {
        connectClusterDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        connectCluster(connectClusterDecoder.baseport(), connectClusterDecoder.port(),
            connectClusterDecoder.clusterHosts(), connectClusterDecoder.localhostName());
        connectionState = ConnectionState.CONNECTED;
    }

    /**
     * Closes the cluster connection
     */
    private void processDisconnectCluster()
    {
        log("Disconnecting from cluster", AttributedStyle.WHITE);
        disconnectCluster();
        connectionState = ConnectionState.NOT_CONNECTED;
        log("Cluster disconnected", AttributedStyle.GREEN);
    }

    /**
     * Marshals the CLI protocol to cluster protocol for Adding an Auction
     *
     * @param messageHeaderDecoder the message header decoder
     * @param buffer               the buffer containing the message
     * @param offset               the offset of the message
     */
    private void processAddInstrument(
        final MessageHeaderDecoder messageHeaderDecoder,
        final MutableDirectBuffer buffer,
        final int offset)
    {
        final String correlationId = UUID.randomUUID().toString();

        addInstrumentDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        addInstrumentEncoder.wrapAndApplyHeader(sendBuffer, 0, messageHeaderEncoder);
        addInstrumentEncoder.correlation(correlationId);
        addInstrumentEncoder.cusip(addInstrumentDecoder.cusip());
        addInstrumentEncoder.enabled(mapBoolean(addInstrumentDecoder.enabled()));
        addInstrumentEncoder.minSize(addInstrumentDecoder.minSize());

        retryingClusterOffer(sendBuffer, MessageHeaderEncoder.ENCODED_LENGTH +
            addInstrumentEncoder.encodedLength());

        pendingMessageManager.addMessage(correlationId, "instrument-add");
    }


    /**
     * Marshals the CLI protocol to cluster protocol for Adding a Participant
     *
     * @param messageHeaderDecoder the message header decoder
     * @param buffer               the buffer containing the message
     * @param offset               the offset of the message
     */
    private void processSetInstrumentEnabled(
        final MessageHeaderDecoder messageHeaderDecoder,
        final MutableDirectBuffer buffer,
        final int offset)
    {
        final String correlationId = UUID.randomUUID().toString();
        setInstrumentEnabledDecoder.wrapAndApplyHeader(buffer, offset, messageHeaderDecoder);
        setInstrumentEnabledEncoder.wrapAndApplyHeader(sendBuffer, 0, messageHeaderEncoder);

        setInstrumentEnabledEncoder.correlation(correlationId);
        setInstrumentEnabledEncoder.cusip(setInstrumentEnabledDecoder.cusip());
        setInstrumentEnabledEncoder.enabled(mapBoolean(setInstrumentEnabledDecoder.enabled()));

        retryingClusterOffer(sendBuffer, MessageHeaderEncoder.ENCODED_LENGTH +
            setInstrumentEnabledEncoder.encodedLength());

        pendingMessageManager.addMessage(correlationId, "instrument-set-enabled");
    }


    /**
     * Disconnects from the cluster
     */
    private void disconnectCluster()
    {
        adminClientEgressListener = null;
        if (aeronCluster != null)
        {
            aeronCluster.close();
        }
        if (mediaDriver != null)
        {
            mediaDriver.close();
        }
    }

    /**
     * Connects to the cluster
     *
     * @param basePort      base port to use
     * @param port          the port to use
     * @param clusterHosts  list of cluster hosts
     * @param localHostName if empty, will be looked up
     */
    private void connectCluster(
        final int basePort,
        final int port,
        final String clusterHosts,
        final String localHostName)
    {
        final List<String> hostnames = Arrays.asList(clusterHosts.split(","));
        final String ingressEndpoints = ClusterConfig.ingressEndpoints(
            hostnames, basePort, ClusterConfig.CLIENT_FACING_PORT_OFFSET);
        final String egressChannel = "aeron:udp?endpoint=" + localHostName + ":" + port;
        adminClientEgressListener = new AdminClientEgressListener(pendingMessageManager);
        adminClientEgressListener.setLineReader(lineReader);
        mediaDriver = MediaDriver.launch(new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .dirDeleteOnStart(true)
            .errorHandler(this::logError)
            .dirDeleteOnShutdown(true));
        aeronCluster = AeronCluster.connect(
            new AeronCluster.Context()
                .egressListener(adminClientEgressListener)
                .egressChannel(egressChannel)
                .ingressChannel(INGRESS_CHANNEL)
                .ingressEndpoints(ingressEndpoints)
                .errorHandler(this::logError)
                .aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        log("Connected to cluster leader, node " + aeronCluster.leaderMemberId(), AttributedStyle.GREEN);
    }

    private void logError(final Throwable throwable)
    {
        log("Error: " + throwable.getMessage(), AttributedStyle.RED);
    }

    /**
     * Sets the line reader to use for input saving while logging
     *
     * @param lineReader line reader to use
     */
    public void setLineReader(final LineReader lineReader)
    {
        this.lineReader = lineReader;
        pendingMessageManager.setLineReader(lineReader);
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

    /**
     * sends to cluster with retry as needed, up to the limit
     *
     * @param buffer buffer containing the message
     * @param length length of the message
     */
    private void retryingClusterOffer(final DirectBuffer buffer, final int length)
    {
        if (connectionState == ConnectionState.CONNECTED)
        {
            int retries = 0;
            do
            {
                final long result = aeronCluster.offer(buffer, 0, length);
                if (result > 0L)
                {
                    return;
                }
                else if (result == Publication.ADMIN_ACTION || result == Publication.BACK_PRESSURED)
                {
                    log("backpressure or admin action on cluster offer", AttributedStyle.YELLOW);
                }
                else if (result == Publication.NOT_CONNECTED || result == Publication.MAX_POSITION_EXCEEDED)
                {
                    log("Cluster is not connected, or maximum position has been exceeded. Message lost.",
                        AttributedStyle.RED);
                    return;
                }

                idleStrategy.idle();
                retries += 1;
                log("failed to send message to cluster. Retrying (" + retries + " of " + RETRY_COUNT + ")",
                    AttributedStyle.YELLOW);
            }
            while (retries < RETRY_COUNT);

            log("Failed to send message to cluster. Message lost.", AttributedStyle.RED);
        }
        else
        {
            log("Not connected to cluster. Connect first", AttributedStyle.RED);
        }
    }

    @Override
    public void onClose()
    {
        if (aeronCluster != null)
        {
            aeronCluster.close();
        }
        if (mediaDriver != null)
        {
            mediaDriver.close();
        }
        runningFlag.set(false);
    }

    private BooleanType mapBoolean(final com.aeroncookbook.rfq.cluster.admin.protocol.BooleanType flag)
    {
        return flag == com.aeroncookbook.rfq.cluster.admin.protocol.BooleanType.TRUE ?
            BooleanType.TRUE : BooleanType.FALSE;

    }
}
