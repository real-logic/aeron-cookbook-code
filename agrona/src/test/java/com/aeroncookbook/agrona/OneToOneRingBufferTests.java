package com.aeroncookbook.agrona;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;

class OneToOneRingBufferTests
{

    private final String testString = "0123456789";

    @Test
    void canSendAndReceive()
    {
        final int bufferLength = 4096 + RingBufferDescriptor.TRAILER_LENGTH;
        final UnsafeBuffer internalBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength));
        final OneToOneRingBuffer ringBuffer = new OneToOneRingBuffer(internalBuffer);
        final MessageCapture capture = new MessageCapture();

        final UnsafeBuffer toSend = new UnsafeBuffer(ByteBuffer.allocateDirect(10));
        toSend.putStringWithoutLengthAscii(0, testString);

        for (int i = 0; i < 10000; i++)
        {
            for (int k = 0; k < 20; k++)
            {
                final boolean success = ringBuffer.write(1, toSend, 0, 10);
                if (!success)
                {
                    System.err.println("Failed to write!");
                }
            }
            ringBuffer.read(capture, 40);
        }

        Assertions.assertEquals(1, capture.receivedStrings.size());
        Assertions.assertTrue(capture.receivedStrings.contains(testString));
        Assertions.assertEquals(200000, capture.count);
        Assertions.assertNotEquals(0, ringBuffer.consumerPosition());
        Assertions.assertNotEquals(0, ringBuffer.producerPosition());
    }

    class MessageCapture implements MessageHandler
    {

        private HashSet<String> receivedStrings = new HashSet<>();
        private int count = 0;

        @Override
        public void onMessage(int msgTypeId, MutableDirectBuffer buffer, int index, int length)
        {
            receivedStrings.add(buffer.getStringWithoutLengthAscii(index, length));
            count++;
        }

    }
}
