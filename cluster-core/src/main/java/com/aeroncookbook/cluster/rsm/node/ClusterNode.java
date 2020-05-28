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

package com.aeroncookbook.cluster.rsm.node;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class ClusterNode
{
    private final ShutdownSignalBarrier barrier;
    private MediaDriver.Context mediaDriverContext;
    private ConsensusModule.Context consensusModuleContext;
    private Archive.Context archiveContext;
    private ClusteredServiceContainer.Context serviceContainerContext;
    private RsmClusteredService service;
    private ClusteredMediaDriver clusteredMediaDriver;
    private ClusteredServiceContainer container;

    public ClusterNode(ShutdownSignalBarrier barrier)
    {
        this.barrier = barrier;
    }

    public void start(final boolean deleteOnStart)
    {
        mediaDriverContext = new MediaDriver.Context();
        consensusModuleContext = new ConsensusModule.Context();
        archiveContext = new Archive.Context();
        serviceContainerContext = new ClusteredServiceContainer.Context();

        service = new RsmClusteredService();

        mediaDriverContext
            .threadingMode(ThreadingMode.SHARED)
            .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
            .errorHandler(Throwable::printStackTrace)
            .terminationHook(barrier::signal)
            .dirDeleteOnShutdown(true)
            .dirDeleteOnStart(true);

        archiveContext
            .recordingEventsEnabled(true)
            .threadingMode(ArchiveThreadingMode.SHARED);

        consensusModuleContext
            .errorHandler(Throwable::printStackTrace)
            .deleteDirOnStart(deleteOnStart);

        serviceContainerContext
            .clusteredService(service)
            .errorHandler(Throwable::printStackTrace);

        clusteredMediaDriver = ClusteredMediaDriver.launch(
            mediaDriverContext,
            archiveContext,
            consensusModuleContext
        );

        container = ClusteredServiceContainer.launch(serviceContainerContext);
    }
}
