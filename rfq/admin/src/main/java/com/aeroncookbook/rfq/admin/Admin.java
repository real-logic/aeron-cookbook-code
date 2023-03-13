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

package com.aeroncookbook.rfq.admin;

import com.aeroncookbook.rfq.admin.cli.CliCommands;
import com.aeroncookbook.rfq.admin.cluster.ClusterInteractionAgent;
import com.aeroncookbook.rfq.admin.util.EnvironmentUtil;
import com.aeroncookbook.rfq.cluster.admin.protocol.ConnectClusterEncoder;
import com.aeroncookbook.rfq.cluster.admin.protocol.MessageHeaderEncoder;
import org.agrona.CloseHelper;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.jline.builtins.ConfigurationPath;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.Builtins;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.Parser;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;

/**
 * Admin client for the cluster main class, working on a direct connection to the cluster
 */
public class Admin
{
    /**
     * Main method
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) throws IOException
    {
        //start the agent used for cluster interaction
        final String prompt = "admin > ";
        final AtomicBoolean running = new AtomicBoolean(true);
        final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy();
        final UnsafeBuffer adminClusterBuffer = new UnsafeBuffer(ByteBuffer.allocate(8192 + TRAILER_LENGTH));
        final OneToOneRingBuffer adminClusterChannel = new OneToOneRingBuffer(adminClusterBuffer);

        final ClusterInteractionAgent clusterInteractionAgent = new ClusterInteractionAgent(adminClusterChannel,
            idleStrategy, running);
        final AgentRunner clusterInteractionAgentRunner = new AgentRunner(idleStrategy, Throwable::printStackTrace,
            null, clusterInteractionAgent);
        AgentRunner.startOnThread(clusterInteractionAgentRunner);

        final Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));

        //start the terminal REPL
        try (Terminal terminal = TerminalBuilder
            .builder()
            .dumb(EnvironmentUtil.tryGetDumbTerminalFromEnv())
            .build())
        {
            final Parser parser = new DefaultParser();
            final ConfigurationPath configPath = new ConfigurationPath(workDir.get(), workDir.get());
            final Builtins builtins = new Builtins(workDir, configPath, null);
            final CliCommands commands = new CliCommands();
            commands.setAdminChannel(adminClusterChannel);
            final PicocliCommands.PicocliCommandsFactory factory = new PicocliCommands.PicocliCommandsFactory();
            final CommandLine cmd = new CommandLine(commands, factory);
            final PicocliCommands picocliCommands = new PicocliCommands(cmd);
            final SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, workDir, null);
            systemRegistry.setCommandRegistries(builtins, picocliCommands);
            final LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(systemRegistry.completer())
                .parser(parser)
                .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                .build();
            builtins.setLineReader(reader);
            commands.setReader(reader);
            factory.setTerminal(terminal);
            clusterInteractionAgent.setLineReader(reader);

            String line;
            terminal.writer().println("-------------------------------------------------");
            terminal.writer().println(" Welcome to the Aeron Cookbook RFQ Cluster Admin");
            terminal.writer().println("-------------------------------------------------");
            terminal.writer().println("");

            autoConnectCluster(adminClusterChannel, terminal.writer());
            logMyUser(terminal.writer());

            while (running.get())
            {
                try
                {
                    systemRegistry.cleanUp();
                    line = reader.readLine(prompt, null, (MaskingCallback)null, null);
                    if ("exit".equalsIgnoreCase(line))
                    {
                        running.set(false);
                        CloseHelper.quietClose(clusterInteractionAgentRunner);
                    }
                    systemRegistry.execute(line);
                }
                catch (final UserInterruptException e)
                {
                    // Ignore
                }
                catch (final EndOfFileException e)
                {
                    return;
                }
                catch (final Exception e)
                {
                    systemRegistry.trace(e);
                }
            }
        }
    }

    private static void logMyUser(final PrintWriter writer)
    {
        final int myParticipantId = EnvironmentUtil.tryGetUserId();
        if (myParticipantId != 0)
        {
            writer.println("Session acting as participant " + myParticipantId);
        }
    }

    /**
     * Auto-connect to the cluster with environment variables if AUTO_CONNECT is true.
     *
     * @param adminClusterChannel the channel to send the connect command to
     * @param writer
     */
    private static void autoConnectCluster(final OneToOneRingBuffer adminClusterChannel, final PrintWriter writer)
    {
        if (!Boolean.parseBoolean(System.getenv("AUTO_CONNECT")))
        {
            writer.println("Not auto-connecting to cluster");
            return;
        }
        final ConnectClusterEncoder connectClusterEncoder = new ConnectClusterEncoder();
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(1024);
        final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
        connectClusterEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder);
        connectClusterEncoder.baseport(9000);
        connectClusterEncoder.port(EnvironmentUtil.tryGetResponsePortFromEnv());
        connectClusterEncoder.clusterHosts(EnvironmentUtil.tryGetClusterHostsFromEnv());
        connectClusterEncoder.localhostName(EnvironmentUtil.getThisHostName());
        adminClusterChannel.write(10, buffer, 0,
            MessageHeaderEncoder.ENCODED_LENGTH + connectClusterEncoder.encodedLength());
    }

}
