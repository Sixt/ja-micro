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

package com.sixt.service.framework;

import com.sixt.service.framework.logging.SixtLogbackContext;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ServicePropertiesTest {

    private ServiceProperties props;

    @Before
    public void setup() {
        props = new ServiceProperties();
    }

    @Test
    public void testInitialize() {
        String args[] = { "-servicePort", "42000", "-logLevel", "DEBUG", "-foo", "bar" };
        props.initialize(args);
        String requiredPasses[] = { "foo" };
        props.ensureProperties(requiredPasses);
    }

    @Test
    public void updateLoggerLogLevel() throws Exception {
        props.logbackContext = mock(SixtLogbackContext.class);

        String args[] = { "-logLevel", "DEBUG" };
        props.initialize(args);

        verify(props.logbackContext).updateLoggerLogLevel("DEBUG");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNumberArguments() {
        String args[] = { "-forgotParameter" };
        props.initialize(args);
    }

    @Test(expected = IllegalStateException.class)
    public void requiredPropMissing() {
        String required[] = { "XXXXXXXXXX" };
        props.ensureProperties(required);
    }

    @Test
    public void propertiesNotSet() {
        props.addProperty("kafkaServer", "test.org:52");
        assertThat(props.getKafkaServer()).isEqualTo("test.org:52");
        assertThat(props.getProperty("blahblah")).isNull();
    }

    @Test
    public void integerValueInvalid() {
        props.addProperty("foo", "bar");
        assertThat(props.getIntegerProperty("foo", 42)).isEqualTo(42);
    }

    @Test
    public void integerValueHappyPath() {
        props.addProperty("foo", "42");
        assertThat(props.getIntegerProperty("foo", 42)).isEqualTo(42);
    }

}
