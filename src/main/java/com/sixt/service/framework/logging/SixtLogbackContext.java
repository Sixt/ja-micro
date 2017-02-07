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
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SixtLogbackContext implements LoggingContext {

    private static final Logger logger = LoggerFactory.getLogger(SixtLogbackContext.class);

    public void updateLoggerLogLevel(String newLevel) {
        this.updateLoggerLogLevel("com.sixt", newLevel);
    }

    public void updateLoggerLogLevel(String loggerName, String newLevel) {
        SixtLogbackContext.logger.info("Starting to update Logger's log level to {}", newLevel);

        List<ch.qos.logback.classic.Logger> loggers
            = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLoggerList();
        for (ch.qos.logback.classic.Logger logger : loggers) {
            String name = logger.getName();
            Level level = logger.getLevel();
            SixtLogbackContext.logger.debug("Logger [{}] has log level {}", name, level);
            if (name.equals(loggerName)) {
                logger.setLevel(Level.toLevel(newLevel));
                SixtLogbackContext.logger.info("Affected Loggers were [{}] from {} to {}", logger, level, newLevel);

                return; // child loggers will be handled by logback itself
            }
        }
    }

}
