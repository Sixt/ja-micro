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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class EventUtils {

    protected static final String META = "meta";
    protected static final String EVENT_TYPE = "eventType";
    protected static final String NAME = "name";

    public static String getEventName(JsonObject event) {
        String eventName = null;
        if (event.has(META)) {
            eventName = event.getAsJsonObject(META).get(NAME).getAsString();
        } else {
            JsonElement eventType = event.get(EVENT_TYPE);
            if (eventType != null) {
                eventName = eventType.getAsString();
            }
        }
        return eventName;
    }
}
