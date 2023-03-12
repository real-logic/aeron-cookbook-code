package com.aeroncookbook.rfq.domain.instrument;

public record Instrument(int id, int securityId, String cusip, boolean enabled, int minSize)
{
}
