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

package com.aeroncookbook.cluster.rsm.client;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.samples.cluster.ClusterConfig;

import java.util.Arrays;
import java.util.List;

public class ClusterClient
{
    public static void main(final String[] args)
    {
        final String ingressEndpoints = ingressEndpoints(Arrays.asList("localhost"));
        RsmClusterClient rsmClusterClient = new RsmClusterClient();

        try (
                MediaDriver mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                        .threadingMode(ThreadingMode.SHARED)
                        .dirDeleteOnStart(true)
                        .dirDeleteOnShutdown(true));
                AeronCluster aeronCluster = AeronCluster.connect(
                        new AeronCluster.Context()
                                .egressListener(rsmClusterClient)
                                .egressChannel("aeron:udp?endpoint=localhost:0")
                                .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                                .ingressChannel("aeron:udp")
                                .ingressEndpoints(ingressEndpoints)))
        {
            rsmClusterClient.setAeronCluster(aeronCluster);
            rsmClusterClient.start();
        }
    }

    public static String ingressEndpoints(final List<String> hostnames)
    {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hostnames.size(); i++)
        {
            sb.append(i).append('=');
            sb.append(hostnames.get(i)).append(':').append(calculatePort(i, 10));
            sb.append(',');
        }

        sb.setLength(sb.length() - 1);
        System.out.println(sb);
        return sb.toString();
    }


    static int calculatePort(final int nodeId, final int offset)
    {
        return ClusterConfig.PORT_BASE + (nodeId * ClusterConfig.PORTS_PER_NODE) + offset;
    }
}
