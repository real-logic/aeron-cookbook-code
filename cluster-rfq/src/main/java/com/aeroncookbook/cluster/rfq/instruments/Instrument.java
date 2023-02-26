package com.aeroncookbook.cluster.rfq.instruments;

public record Instrument(int id, int securityId, String cusip, boolean enabled, int minSize)
{
}
