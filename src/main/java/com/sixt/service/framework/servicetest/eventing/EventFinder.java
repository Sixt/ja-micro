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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.protobuf.Message;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class EventFinder implements Callable<Map<String, Message>> {

    private static final Logger logger = LoggerFactory.getLogger(EventFinder.class);

    private final Map<String, Class> expectedEvents;
    private final ServiceTestEventHandler eventHandler;

    public EventFinder(ServiceTestEventHandler eventHandler, Map<String, Class> expectedEvents) {
        this.eventHandler = eventHandler;
        this.expectedEvents = expectedEvents;
    }

    @Override
    public Map<String, Message> call() throws Exception {
        Map<String, Message> foundEvents = Maps.newHashMap();
        List<JsonObject> capturedEvents = Lists.newArrayList();

        while (foundEvents.size() != expectedEvents.size()) {
            logger.info("Polling kafka...");

            Thread.sleep(500);

            capturedEvents.addAll(eventHandler.getAllJsonEvents());

            for (JsonObject event : capturedEvents) {

                String eventName = EventUtils.getEventName(event);
                if (eventName != null) {
                    Class eventClass = expectedEvents.get(eventName);

                    if (eventClass != null) {
                        logger.info("Found matching event: " + eventName);
                        foundEvents.put(eventName, ProtobufUtil.jsonToProtobuf(event.toString(), eventClass));
                    }
                }
            }
        }

        return foundEvents;
    }

}