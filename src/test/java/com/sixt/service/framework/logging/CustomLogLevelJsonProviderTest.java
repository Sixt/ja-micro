/**
 * Copyright 2016-2017 Sixt GmbH & Co. Autovermietung KG
 * Licensed under the Apache License, Version 2.0 (the "License"); you may 
 * not use this file except in compliance with the License. You may obtain a 
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 */

package com.sixt.service.framework.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class CustomLogLevelJsonProviderTest {

    @Test
    public void test() throws IOException {
        CustomLogLevelJsonProvider provider = new CustomLogLevelJsonProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("dynamic_logger");
        ILoggingEvent event = new LoggingEvent("", logger, Level.ERROR, "", null, null);
        provider.writeTo(generator, event);
        event = new LoggingEvent("", logger, Level.WARN, "", null, null);
        provider.writeTo(generator, event);
        event = new LoggingEvent("", logger, Level.INFO, "", null, null);
        provider.writeTo(generator, event);
        event = new LoggingEvent("", logger, Level.DEBUG, "", null, null);
        provider.writeTo(generator, event);
        event = new LoggingEvent("", logger, Level.TRACE, "", null, null);
        provider.writeTo(generator, event);
        event = new LoggingEvent("", logger, Level.ALL, "", null, null);
        provider.writeTo(generator, event);
        event = new LoggingEvent("", logger, Level.OFF, "", null, null);
        provider.writeTo(generator, event);
        verify(generator, times(1)).writeStringField("level", "error");
        verify(generator, times(1)).writeStringField("level", "warning");
        verify(generator, times(1)).writeStringField("level", "info");
        verify(generator, times(1)).writeStringField("level", "debug");
        verify(generator, times(1)).writeStringField("level", "trace");
        verify(generator, times(1)).writeStringField("level", "all");
        verify(generator, times(1)).writeStringField("level", "off");
    }

}
