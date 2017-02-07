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

import com.sixt.service.framework.ServiceProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationManagerTest {

    private static final String UPDATE_PROP = "prop1";
    private static final String UPDATE_VALUE = "new value";
    private static final String NO_UPDATE_VALUE = "val1";

    private static final String NEW_PROP = "prop3";
    private static final String NEW_VALUE = "new value 3";

    private ServiceProperties props = new ServiceProperties();
    private ConfigurationManager cm = new ConfigurationManager(props);

    @Before
    public void setup() {
        props = new ServiceProperties();
        props.addProperty("prop1", "val1");
        props.addProperty("prop2", "val2");
        cm = new ConfigurationManager(props);


    }

    @Test
    public void updatePropertyTest() {
        StringBuilder log = new StringBuilder();

        cm.registerChangeCallback(UPDATE_PROP, new ChangeCallback() {
            @Override
            public void entryChanged(String name, String value) {
                if (name.equals(UPDATE_PROP)) {
                    assertThat(value).isEqualTo(UPDATE_VALUE);
                    log.append("updated");
                } else {
                    fail("No other props should be updated. Notification received for: " + name + ", value: " + value);
                }
            }
        });

        Map<String, String> updatedValues = new HashMap<>();
        updatedValues.put(UPDATE_PROP, UPDATE_VALUE);

        int propsCountBefore = cm.serviceProps.getAllProperties().size();

        cm.processValues(updatedValues);

        assertThat(log.toString()).isEqualTo("updated");
        int propsCountAfter = cm.serviceProps.getAllProperties().size();
        assertThat(propsCountBefore).isEqualTo(propsCountAfter);
    }

    @Test
    public void newPropertyTest() {
        StringBuilder log = new StringBuilder();

        cm.registerChangeCallback(NEW_PROP, new ChangeCallback() {
            @Override
            public void entryChanged(String name, String value) {
                if (name.equals(NEW_PROP)) {
                    assertThat(value).isEqualTo(NEW_VALUE);
                    log.append("created");
                } else {
                    fail("No other props should be updated. Notification received for: " + name + ", value: " + value);
                }
            }
        });

        Map<String, String> updatedValues = new HashMap<>();
        updatedValues.put(NEW_PROP, NEW_VALUE);

        int propsCountBefore = cm.serviceProps.getAllProperties().size();

        cm.processValues(updatedValues);

        assertThat(log.toString()).isEqualTo("created");
        int propsCountAfter = cm.serviceProps.getAllProperties().size();
        assertThat(propsCountBefore + 1).isEqualTo(propsCountAfter);
    }

    @Test
    public void noUpdatePropertyTest() {
        cm.registerChangeCallback(UPDATE_PROP, new ChangeCallback() {
            @Override
            public void entryChanged(String name, String value) {
                fail("No props should be updated. Notification received for: " + name + ", value: " + value);
            }
        });

        Map<String, String> updatedValues = new HashMap<>();
        updatedValues.put(UPDATE_PROP, NO_UPDATE_VALUE);

        cm.processValues(updatedValues);
    }

}
