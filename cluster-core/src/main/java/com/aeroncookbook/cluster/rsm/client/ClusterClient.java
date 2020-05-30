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

package com.aeroncookbook.cluster.rsm.client;

import io.aeron.CommonContext;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;

import java.util.Arrays;
import java.util.List;


public class ClusterClient
{
    private static MediaDriver clientMediaDriver;
    private static AeronCluster clusterClient;
    private static final int CLIENT_FACING_PORT_OFFSET = 3;
    private static final int PORT_BASE = 9000;
    private static final int PORTS_PER_NODE = 100;

    public static void main(final String[] args)
    {
        final String aeronDirName = CommonContext.getAeronDirectoryName() + "-client";
        final int egressPort = 19000;

        RsmClusterClient rsmClusterClient = new RsmClusterClient();

        clientMediaDriver = MediaDriver.launch(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnStart(true)
                .errorHandler(Throwable::printStackTrace)
                .aeronDirectoryName(aeronDirName)
        );

        clusterClient = AeronCluster.connect(
                    new AeronCluster.Context()
                    .egressListener(rsmClusterClient)
                    .egressChannel("aeron:udp?endpoint=localhost:" + egressPort)
                    .aeronDirectoryName(clientMediaDriver.aeronDirectoryName())
                    .ingressChannel("aeron:udp")
                    .ingressEndpoints(ingressEndpoints(Arrays.asList("localhost"))));

        rsmClusterClient.setAeronCluster(clusterClient);
        rsmClusterClient.start();
    }

    public static String ingressEndpoints(final List<String> hostnames)
    {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hostnames.size(); i++)
        {
            sb.append(i).append('=');
            sb.append(hostnames.get(i)).append(':').append(calculatePort(i, CLIENT_FACING_PORT_OFFSET));
            sb.append(',');
        }

        sb.setLength(sb.length() - 1);

        return sb.toString();
    }

    /* As seen in BasicAuctionClusteredServiceNode in Aeron Samples */
    static int calculatePort(final int nodeId, final int offset)
    {
        return PORT_BASE + (nodeId * PORTS_PER_NODE) + offset;
    }
}
