package com.aeroncookbook.archive;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class SimplestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SimplestCase.class);

    public static void main(String[] args)
    {
        final String channel = "aeron:ipc";
        final int streamCapture = 16;
        final int streamReplay = 17;
        final String message = "my message";

        final IdleStrategy idle = new SleepingIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        //setup archiving media driver
        //create pub
        //create archive client subs
        //replay
    }
}
