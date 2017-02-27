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
import com.google.protobuf.Message;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.kafka.KafkaSubscriber;
import com.sixt.service.framework.kafka.KafkaSubscriberFactory;
import com.sixt.service.framework.kafka.KafkaTopicInfo;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.test.TestEvent;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ServiceTestEventHandlerTest {

    private ServiceTestEventHandler eventHandler;
    private final String ONE_TEST_EVENT_NAME = "OneTestEvent";
    private final String VEHICLE_ID = "e4a054c8-6e51-435a-aa2f-d5c85a571be0";

    @Before
    public void setUp() throws Exception {
        ServiceProperties properties = new ServiceProperties();
        properties.addProperty("kafkaServer", "localhost:9092");
        KafkaSubscriberFactory kafkaFactory = new KafkaSubscriberFactory(properties);
        eventHandler = new ServiceTestEventHandler(kafkaFactory);
        eventHandler.initialize("events");
    }

    @Test
    public void getAllJsonEvents_NoEvents() {
        assertThat(eventHandler.kafkaSubscriber).isNotNull();

        List<JsonObject> jsonEventMessages = eventHandler.getAllJsonEvents();
        assertThat(jsonEventMessages.isEmpty()).isTrue();
    }

    @Test
    public void getEventsOfType_NoEvents() {
        assertThat(eventHandler.kafkaSubscriber).isNotNull();

        List<TestEvent.OneTestEvent> foundEvents = eventHandler.getEventsOfType(ONE_TEST_EVENT_NAME, TestEvent.OneTestEvent.class);
        assertThat(foundEvents.isEmpty()).isTrue();
    }

    @Test
    public void getEvent_NoEvent() {
        assertThat(eventHandler.kafkaSubscriber).isNotNull();
        TestEvent.OneTestEvent foundEvent = eventHandler.getEvent(ONE_TEST_EVENT_NAME, TestEvent.OneTestEvent.class,
                (TestEvent.OneTestEvent e) -> e.getVehicleId().equals(VEHICLE_ID), 500);
        assertThat(foundEvent).isNull();
    }

    @Test
    @Ignore("We need a better timeout handling, currently this test is running quite long waiting for timeout")
    public void getExpectedEvents_NoEvents() {
        assertThat(eventHandler.kafkaSubscriber).isNotNull();

        Map<String, Class> expectedEvents = new HashMap<>();
        expectedEvents.put(ONE_TEST_EVENT_NAME, TestEvent.OneTestEvent.class);
        Map<String, Message> foundEvents = eventHandler.getExpectedEvents(expectedEvents);
        assertThat(foundEvents.isEmpty()).isTrue();
    }

    @Test
    public void getAllJsonEvents_EventsPresent() {
        eventHandler.kafkaSubscriber = mock(KafkaSubscriber.class);
        eventHandler.eventReceived(getOneTestEventBody(), new KafkaTopicInfo("topic", 0, 0, null));

        List<JsonObject> jsonEventMessages = eventHandler.getAllJsonEvents();

        assertThat(jsonEventMessages.size()).isEqualTo(1);
        assertThat(jsonEventMessages.get(0).getAsJsonObject("meta").getAsJsonPrimitive("name").getAsString()).isEqualTo(ONE_TEST_EVENT_NAME);
        assertThat(jsonEventMessages.get(0).getAsJsonPrimitive("vehicle_id").getAsString()).isEqualTo(VEHICLE_ID);
        verify(eventHandler.kafkaSubscriber).consume(any(KafkaTopicInfo.class));
    }

    @Test
    public void getEventsOfType_EventsPresent() {
        eventHandler.kafkaSubscriber = mock(KafkaSubscriber.class);
        eventHandler.eventReceived(getOneTestEventBody(), new KafkaTopicInfo("topic", 0, 0, null));

        List<TestEvent.OneTestEvent> foundEvents = eventHandler.getEventsOfType(ONE_TEST_EVENT_NAME, TestEvent.OneTestEvent.class);
        assertThat(foundEvents.size()).isEqualTo(1);
        verify(eventHandler.kafkaSubscriber).consume(any(KafkaTopicInfo.class));
    }

    @Test
    public void getEvent_EventsPresent() {
        eventHandler.kafkaSubscriber = mock(KafkaSubscriber.class);
        eventHandler.eventReceived(getOneTestEventBody(), new KafkaTopicInfo("topic", 0, 0, null));

        TestEvent.OneTestEvent foundEvent = eventHandler.getEvent(ONE_TEST_EVENT_NAME, TestEvent.OneTestEvent.class,
                (TestEvent.OneTestEvent e) -> e.getVehicleId().equals(VEHICLE_ID), 500);
        assertThat(foundEvent).isNotNull();
        assertThat(foundEvent.getMeta().getName()).isEqualTo(ONE_TEST_EVENT_NAME);
        verify(eventHandler.kafkaSubscriber).consume(any(KafkaTopicInfo.class));
    }

    @Test
    public void getExpectedEvents_EventsPresent() {
        eventHandler.kafkaSubscriber = mock(KafkaSubscriber.class);
        eventHandler.eventReceived(getOneTestEventBody(), new KafkaTopicInfo("topic", 0, 0, null));

        Map<String, Class> expectedEvents = new HashMap<>();
        expectedEvents.put(ONE_TEST_EVENT_NAME, TestEvent.OneTestEvent.class);
        Map<String, Message> foundEvents = eventHandler.getExpectedEvents(expectedEvents);
        assertThat(foundEvents.size()).isEqualTo(1);
        verify(eventHandler.kafkaSubscriber).consume(any(KafkaTopicInfo.class));
    }

    /**
     * Helper methods
     */

    private String getOneTestEventBody() {
        TestEvent.Meta meta = TestEvent.Meta.newBuilder().setName(ONE_TEST_EVENT_NAME).build();
        TestEvent.OneTestEvent testEvent = TestEvent.OneTestEvent.newBuilder().setMeta(meta)
                .setVehicleId(VEHICLE_ID).build();

        JsonObject eventJson = ProtobufUtil.protobufToJson(testEvent);

        return eventJson.toString();
    }

}