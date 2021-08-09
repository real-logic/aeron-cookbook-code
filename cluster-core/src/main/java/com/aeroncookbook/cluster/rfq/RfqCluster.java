/*
 * Copyright 2019-2021 Shaun Laurens.
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

package com.aeroncookbook.cluster.rfq;

import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.samples.cluster.ClusterConfig;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.ShutdownSignalBarrier;

import java.util.Arrays;

public class RfqCluster
{
    public static void main(final String[] args)
    {
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        final ClusterConfig clusterConfig = ClusterConfig.create(
                0, Arrays.asList("localhost"), Arrays.asList("localhost"), 9000, new RfqClusteredService());

        clusterConfig.mediaDriverContext().errorHandler(errorHandler("Media Driver"));
        clusterConfig.archiveContext().errorHandler(errorHandler("Archive"));
        clusterConfig.aeronArchiveContext().errorHandler(errorHandler("Aeron Archive"));
        clusterConfig.consensusModuleContext().errorHandler(errorHandler("Consensus Module"));
        clusterConfig.clusteredServiceContext().errorHandler(errorHandler("Clustered Service"));

        //todo remove; seems to be an issue with ClusterConfig with 1.35.0
        clusterConfig.consensusModuleContext().ingressChannel("aeron:udp?endpoint=localhost:9010|term-length=64k");

        try (
                ClusteredMediaDriver clusteredMediaDriver = ClusteredMediaDriver.launch(
                        clusterConfig.mediaDriverContext(),
                        clusterConfig.archiveContext(),
                        clusterConfig.consensusModuleContext());
                ClusteredServiceContainer container = ClusteredServiceContainer.launch(
                        clusterConfig.clusteredServiceContext()))
        {
            System.out.println("Started Cluster Node...");
            barrier.await();
            System.out.println("Exiting");
        }
    }

    private static ErrorHandler errorHandler(final String context)
    {
        return
                (Throwable throwable) ->
                {
                    System.err.println(context);
                    throwable.printStackTrace(System.err);
                };
    }
}
