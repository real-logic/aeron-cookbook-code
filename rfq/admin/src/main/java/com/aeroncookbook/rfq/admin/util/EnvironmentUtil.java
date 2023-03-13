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

package com.aeroncookbook.rfq.admin.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import static java.lang.Integer.parseInt;

/**
 * Utility class for cluster connection
 */
public final class EnvironmentUtil
{
    private EnvironmentUtil()
    {
        // helper class
    }

    /**
     * Tries to get the cluster hosts from the environment variable CLUSTER_ADDRESSES. If that is not set, it will try
     * to get it from the system property cluster.addresses. If that is not set, it will return localhost.
     *
     * @return the cluster hosts
     */
    public static String tryGetClusterHostsFromEnv()
    {
        String clusterAddresses = System.getenv("CLUSTER_ADDRESSES");
        if (null == clusterAddresses || clusterAddresses.isEmpty())
        {
            clusterAddresses = System.getProperty("cluster.addresses", "localhost");
        }
        return clusterAddresses;
    }

    /**
     * Tries to get the response port from the environment variable RESPONSE_PORT. If that is not set, it will try to
     * get it from the system property response.port. If that is not set, it will return 0.
     *
     * This port is the port used by the admin process to open a port for the cluster to connect to.
     * It could be (and typically is) ephemeral, however, kubernetes prefers that services have well-defined ports.
     *
     * Ephemeral ports defined with value 0 for the port.
     *
     * @return the response port
     */
    public static int tryGetResponsePortFromEnv()
    {
        String responsePort = System.getenv("RESPONSE_PORT");
        if (null == responsePort || responsePort.isEmpty())
        {
            responsePort = System.getProperty("response.port", "0");
        }
        return parseInt(responsePort);
    }

    /**
     * Tries to get the participant id from the environment variable PARTICIPANT_ID.
     * If that is not set, it will try to get it from the system property participant.id.
     * If that is not set, it will return 0.
     *
     * @return the participant id
     */
    public static int tryGetUserId()
    {
        String responsePort = System.getenv("USER_ID");
        if (null == responsePort || responsePort.isEmpty())
        {
            responsePort = System.getProperty("user.id", "0");
        }
        return parseInt(responsePort);
    }

    /**
     * Reads DUMB_TERMINAL from the environment variable DUMB_TERMINAL. If that is not set, it will return false
     *
     * @return whether the terminal is expected to be dumb
     */
    public static boolean tryGetDumbTerminalFromEnv()
    {
        if (!Boolean.parseBoolean(System.getenv("DUMB_TERMINAL")))
        {
            return false;
        }
        return Boolean.parseBoolean(System.getenv("DUMB_TERMINAL"));
    }

    /**
     * Gets the hostname of the current machine
     *
     * @return the hostname, or localhost if it cannot be determined
     */
    public static String getThisHostName()
    {
        try
        {
            final Enumeration<NetworkInterface> interfaceEnumeration = NetworkInterface.getNetworkInterfaces();
            while (interfaceEnumeration.hasMoreElements())
            {
                final var networkInterface = interfaceEnumeration.nextElement();

                if (networkInterface.getName().startsWith("eth0"))
                {
                    final Enumeration<InetAddress> interfaceAddresses = networkInterface.getInetAddresses();
                    while (interfaceAddresses.hasMoreElements())
                    {
                        if (interfaceAddresses.nextElement() instanceof Inet4Address inet4Address)
                        {
                            return inet4Address.getHostAddress();
                        }
                    }
                }
            }
        }
        catch (final Exception e)
        {
            // ignore
        }
        return "localhost";
    }
}
