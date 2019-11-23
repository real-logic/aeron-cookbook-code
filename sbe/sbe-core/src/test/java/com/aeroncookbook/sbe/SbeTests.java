package com.aeroncookbook.sbe;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

public class SbeTests
{

    public static final String TEMPLATE_IDS_DO_NOT_MATCH = "Template ids do not match";
    public static final String A_MESSAGE = "a message";
    public static final String MESSAGE_A = "message a";

    @Test
    public void canWriteReadHeader()
    {
        final SampleAEncoder encoder = new SampleAEncoder();
        final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(128);
        final UnsafeBuffer directBuffer = new UnsafeBuffer(byteBuffer);

        encoder.wrapAndApplyHeader(directBuffer, 0, messageHeaderEncoder);
        encoder.sequence(123L);
        encoder.enumField(SampleEnum.VALUE_1);
        encoder.message("a message 1");

        final SampleADecoder decoder = new SampleADecoder();
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        int bufferOffset = 0;
        headerDecoder.wrap(directBuffer, bufferOffset);

        // Lookup the applicable flyweight to decode this type of message based on templateId and version.
        final int templateId = headerDecoder.templateId();
        if (templateId != SampleADecoder.TEMPLATE_ID)
        {
            throw new IllegalStateException(TEMPLATE_IDS_DO_NOT_MATCH);
        }

        final int actingBlockLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        bufferOffset += headerDecoder.encodedLength();
        decoder.wrap(directBuffer, bufferOffset, actingBlockLength, actingVersion);

        Assertions.assertEquals(123, decoder.sequence());
        Assertions.assertEquals(SampleEnum.VALUE_1, decoder.enumField());
        Assertions.assertEquals("a message 1", decoder.message());
    }

    @Test
    public void dataCanBeCorrupted()
    {
        final SampleCorruptionEncoder encoder = new SampleCorruptionEncoder();
        final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(128);
        final UnsafeBuffer directBuffer = new UnsafeBuffer(byteBuffer);

        encoder.wrapAndApplyHeader(directBuffer, 0, messageHeaderEncoder);
        encoder.message2(A_MESSAGE);
        encoder.message1(MESSAGE_A);
        encoder.sequence1(123L);
        encoder.sequence2(321L);

        final SampleCorruptionDecoder decoder = new SampleCorruptionDecoder();
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        int bufferOffset = 0;
        headerDecoder.wrap(directBuffer, bufferOffset);

        // Lookup the applicable flyweight to decode this type of message based on templateId and version.
        final int templateId = headerDecoder.templateId();
        if (templateId != SampleCorruptionDecoder.TEMPLATE_ID)
        {
            throw new IllegalStateException(TEMPLATE_IDS_DO_NOT_MATCH);
        }

        final int actingBlockLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        bufferOffset += headerDecoder.encodedLength();
        decoder.wrap(directBuffer, bufferOffset, actingBlockLength, actingVersion);

        Assertions.assertEquals(123, decoder.sequence1());
        Assertions.assertNotEquals(MESSAGE_A, decoder.message1());
        Assertions.assertEquals(321, decoder.sequence2());
        Assertions.assertNotEquals(A_MESSAGE, decoder.message2());
    }

    @Test
    public void dataCanBeReadCorrectlyWhenWrittenCorrectly()
    {
        final SampleCorruptionEncoder encoder = new SampleCorruptionEncoder();
        final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(128);
        final UnsafeBuffer directBuffer = new UnsafeBuffer(byteBuffer);

        encoder.wrapAndApplyHeader(directBuffer, 0, messageHeaderEncoder);
        encoder.sequence1(123L);
        encoder.sequence2(321L);
        encoder.message1(MESSAGE_A);
        encoder.message2(A_MESSAGE);

        final SampleCorruptionDecoder decoder = new SampleCorruptionDecoder();
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        int bufferOffset = 0;
        headerDecoder.wrap(directBuffer, bufferOffset);

        // Lookup the applicable flyweight to decode this type of message based on templateId and version.
        final int templateId = headerDecoder.templateId();
        if (templateId != SampleCorruptionDecoder.TEMPLATE_ID)
        {
            throw new IllegalStateException(TEMPLATE_IDS_DO_NOT_MATCH);
        }

        final int actingBlockLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        bufferOffset += headerDecoder.encodedLength();
        decoder.wrap(directBuffer, bufferOffset, actingBlockLength, actingVersion);

        Assertions.assertEquals(123, decoder.sequence1());
        Assertions.assertEquals(321, decoder.sequence2());
        Assertions.assertEquals(MESSAGE_A, decoder.message1());
        Assertions.assertEquals(A_MESSAGE, decoder.message2());
    }
}
