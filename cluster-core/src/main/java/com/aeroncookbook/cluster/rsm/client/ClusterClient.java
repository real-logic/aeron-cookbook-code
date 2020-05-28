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

public class ClusterClient
{
    private static MediaDriver clientMediaDriver;
    private static AeronCluster client;

    public static void main(final String[] args)
    {
        final String aeronDirName = CommonContext.getAeronDirectoryName() + "-client";

        RsmClusterClient rsmClusterClient = new RsmClusterClient();

        clientMediaDriver = MediaDriver.launch(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnStart(true)
                .errorHandler(Throwable::printStackTrace)
                .aeronDirectoryName(aeronDirName)
        );

        client = AeronCluster.connect(
            new AeronCluster.Context()
                .errorHandler(Throwable::printStackTrace)
                .egressListener(rsmClusterClient)
                .aeronDirectoryName(aeronDirName)
        );

    }
}
