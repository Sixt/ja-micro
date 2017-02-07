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

package com.sixt.service.framework.servicetest.eventing;

import com.google.gson.JsonObject;
import org.junit.Test;

import static com.sixt.service.framework.servicetest.eventing.EventUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

public class EventUtilsTest {

    private static final String EVENT_NAME = "event-name";

    @Test
    public void getEventName_FromMeta() throws Exception {
        JsonObject event = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty(NAME, EVENT_NAME);
        event.add(META, meta);

        String eventName = EventUtils.getEventName(event);

        assertThat(eventName).isEqualTo(EVENT_NAME);
    }

    @Test
    public void getEventName_FromEventType() throws Exception {
        JsonObject event = new JsonObject();
        event.addProperty(EVENT_TYPE, EVENT_NAME);

        String eventName = getEventName(event);

        assertThat(eventName).isEqualTo(EVENT_NAME);
    }

    @Test
    public void getEventName_NoEventName_ReturnNull() throws Exception {
        String eventName = getEventName(new JsonObject());

        assertThat(eventName).isNull();
    }
}