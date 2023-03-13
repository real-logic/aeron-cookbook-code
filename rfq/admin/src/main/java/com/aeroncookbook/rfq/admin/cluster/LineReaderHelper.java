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

package com.aeroncookbook.rfq.admin.cluster;

import org.jline.reader.LineReader;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for logging to the terminal
 */
public final class LineReaderHelper
{
    private LineReaderHelper()
    {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LineReaderHelper.class);

    /**
     * Logs a message to the terminal if available or to the logger if not
     *
     * @param lineReader line reader
     * @param message message to log
     * @param color message color to use
     */
    public static void log(final LineReader lineReader, final String message, final int color)
    {
        if (lineReader == null)
        {
            LOGGER.info(message);
        }
        else
        {
            final String s = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(color))
                .append(message)
                .toAnsi(lineReader.getTerminal());

            if (lineReader.isReading())
            {
                lineReader.getTerminal().puts(InfoCmp.Capability.carriage_return);
                lineReader.getTerminal().writer().println(s);
                lineReader.callWidget(LineReader.REDRAW_LINE);
                lineReader.callWidget(LineReader.REDISPLAY);
                lineReader.getTerminal().writer().flush();
            }
            else
            {
                lineReader.getTerminal().writer().println(s);
            }
        }
    }
}
