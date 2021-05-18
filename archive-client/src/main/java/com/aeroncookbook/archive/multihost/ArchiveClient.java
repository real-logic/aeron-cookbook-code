package com.aeroncookbook.archive.multihost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveClient.class);

    public static void main(String[] args)
    {
        if (args.length != 3)
        {
            LOGGER.error("requires 3 parameters: the host");
        }
    }
}
