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

package com.sixt.service.framework.configuration;

import com.sixt.service.framework.logging.LoggingContext;

import javax.validation.constraints.NotNull;

/**
 * This class should update the current log level when the callback is fired on configuration change.
 */
public class LogLevelChangeCallback implements ChangeCallback {

    private LoggingContext loggingContext;

    @NotNull
    public LogLevelChangeCallback(LoggingContext loggingContext) {
        this.loggingContext = loggingContext;
    }

    @Override
    public void entryChanged(String name, String value) {
        this.loggingContext.updateLoggerLogLevel("" + value);
    }
}
