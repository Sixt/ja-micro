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
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.JsonWritingUtils;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;

import java.io.IOException;

public class CustomLogLevelJsonProvider extends LogLevelJsonProvider {
    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String level;
        switch (event.getLevel().toInt()) {
            case Level.ERROR_INT:
                level = "error";
                break;
            case Level.WARN_INT:
                level = "warning";
                break;
            case Level.INFO_INT:
                level = "info";
                break;
            case Level.DEBUG_INT:
                level = "debug";
                break;
            case Level.TRACE_INT:
                level = "trace";
                break;
            case Level.ALL_INT:
                level = "all";
                break;
            case Level.OFF_INT:
                level = "off";
                break;
            default:
                level = "???";
        }
        JsonWritingUtils.writeStringField(
                generator, getFieldName(), level);
    }
}