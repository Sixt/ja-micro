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

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.composite.JsonProvider;
import net.logstash.logback.composite.JsonProviders;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static org.assertj.core.api.Assertions.assertThat;

public class SixtJsonEncoderTest {

    @Test
    public void overrideLogLevelProvider() {
        SixtJsonEncoder encoder = new SixtJsonEncoder();
        JsonProviders<ILoggingEvent> providers = encoder.getProviders();
        int defaultCount = 0;
        int overrideCount = 0;

        for (JsonProvider<ILoggingEvent> provider : providers.getProviders()) {
            if (provider instanceof LogLevelJsonProvider &&
                    !(provider instanceof CustomLogLevelJsonProvider)) {
                defaultCount++;
            } else if (provider instanceof CustomLogLevelJsonProvider) {
                overrideCount++;
            }
        }
        assertThat(defaultCount).isEqualTo(0);
        assertThat(overrideCount).isEqualTo(1);
    }

    private static final Logger logger = LoggerFactory.getLogger(SixtJsonEncoderTest.class);

    @Test
    public void extraJsonField() {
        logger.error("This is a test", keyValue("category", "traffic"));
    }
}
