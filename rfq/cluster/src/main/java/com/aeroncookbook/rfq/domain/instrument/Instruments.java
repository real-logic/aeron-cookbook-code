/*
 * Copyright 2019-2023 Adaptive Financial Consulting Ltd.
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

package com.aeroncookbook.rfq.domain.instrument;

import com.aeroncookbook.rfq.infra.ClusterClientResponder;
import org.agrona.collections.Object2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The instrument domain model.
 */
public class Instruments
{
    private static final int DEFAULT_MIN_VALUE = 0;
    private static final Logger LOGGER = LoggerFactory.getLogger(Instruments.class);
    private final ClusterClientResponder clusterClientResponder;

    private final Object2ObjectHashMap<String, Instrument> instrumentByCusip = new Object2ObjectHashMap<>();

    /**
     * Constructor for instrument domain model object.
     *
     * @param clusterClientResponder the responder to which events are sent
     */
    public Instruments(final ClusterClientResponder clusterClientResponder)
    {
        this.clusterClientResponder = clusterClientResponder;
    }

    /**
     * Adds an instrument to the domain model.
     *
     * @param addType the type of add operation
     * @param correlation the correlation id of the request
     * @param cusip   the cusip of the instrument
     * @param enabled the enabled flag of the instrument
     * @param minSize the minimum size of the instrument
     */
    public void addInstrument(
        final InstrumentAddType addType,
        final String correlation,
        final String cusip,
        final boolean enabled,
        final int minSize)
    {
        final Instrument instrument = new Instrument(cusip, enabled, minSize);
        instrumentByCusip.put(cusip, instrument);

        if (addType == InstrumentAddType.INTERACTIVE)
        {
            LOGGER.info("Added instrument {} to domain model", cusip);
            clusterClientResponder.sendInstrumentAdded(correlation);
        }
    }

    /**
     * Sets the enabled flag for an instrument.
     *
     * @param correlation the correlation id of the request
     * @param cusip   the cusip of the instrument
     * @param enabled the enabled flag of the instrument
     */
    public void setEnabledFlagForCusip(final String correlation, final String cusip, final boolean enabled)
    {
        final Instrument instrument = instrumentByCusip.get(cusip);
        if (instrument != null)
        {
            instrument.setEnabled(enabled);
            LOGGER.info("Set enabled flag for instrument {} to {}", cusip, enabled);
            clusterClientResponder.sendInstrumentEnabledFlagSet(correlation, true);
        }
        else
        {
            clusterClientResponder.sendInstrumentEnabledFlagSet(correlation, false);
        }
    }

    /**
     * Returns the enabled flag for an instrument.
     *
     * @param cusip the cusip of the instrument
     * @return the enabled flag for the instrument
     */
    public boolean isInstrumentEnabled(final String cusip)
    {
        final Instrument instrument = instrumentByCusip.get(cusip);
        if (instrument == null)
        {
            return false;
        }
        return instrument.isEnabled();
    }

    /**
     * Returns true if the instrument is valid.
     *
     * @param cusip the cusip of the instrument
     * @return true if the instrument is valid
     */
    public boolean isValidCusip(final String cusip)
    {
        return instrumentByCusip.containsKey(cusip);
    }

    /**
     * Returns the minimum size for an instrument.
     *
     * @param cusip the cusip of the instrument
     * @return the minimum size for the instrument
     */
    public int getMinSize(final String cusip)
    {
        final Instrument instrument = instrumentByCusip.get(cusip);
        if (instrument == null)
        {
            return DEFAULT_MIN_VALUE;
        }
        return instrument.getMinSize();
    }

    /**
     * Returns the number of instruments in the domain model.
     *
     * @return the number of instruments in the domain model
     */
    public int instrumentCount()
    {
        return instrumentByCusip.size();
    }

    /**
     * Emits a list of instruments to the session.
     *
     * @param correlation the correlation id of the request
     */
    public void listInstruments(final String correlation)
    {
        clusterClientResponder.sendInstruments(correlation, instrumentByCusip.values().stream().toList());
    }
}
