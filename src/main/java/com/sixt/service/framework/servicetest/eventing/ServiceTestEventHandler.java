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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Message;
import com.sixt.service.framework.kafka.EventReceivedCallback;
import com.sixt.service.framework.kafka.KafkaSubscriber;
import com.sixt.service.framework.kafka.KafkaSubscriberFactory;
import com.sixt.service.framework.kafka.KafkaTopicInfo;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

@Singleton
public class ServiceTestEventHandler implements EventReceivedCallback<String> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceTestEventHandler.class);

    private static final String GROUP_ID = "service-integration-test";
    private static final int TIMEOUT = 30;
    private final static int POLL_TIME = 200;
    private final static int CORE_POOL_SIZE = 1;
    private final static int MAX_POOL_SIZE = 1;
    private final static int KEEP_ALIVE_TIME = 15;

    private final KafkaSubscriberFactory<String> subscriberFactory;
    @VisibleForTesting
    protected KafkaSubscriber kafkaSubscriber;
    private ConcurrentLinkedQueue<JsonObject> readEvents = new ConcurrentLinkedQueue<>();

    @Inject
    public ServiceTestEventHandler(KafkaSubscriberFactory<String> kafkaFactory) {
        this.subscriberFactory = kafkaFactory;
    }

    public void initialize(String topic) {
        this.kafkaSubscriber = subscriberFactory.newBuilder(topic, this)
                .withPollTime(POLL_TIME)
                .withGroupId(GROUP_ID)
                .withThreadPool(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME)
                .build();
    }

    @Override
    public void eventReceived(String message, KafkaTopicInfo topicInfo) {
        if (message == null) {
            logger.debug("Received kafka message from topic {} with null body", topicInfo.getTopic());
        } else {
            JsonObject jsonObject = new JsonParser().parse(message).getAsJsonObject();
            readEvents.add(jsonObject);
            kafkaSubscriber.consume(topicInfo);
        }
    }

    public void clearReadEvents() {
        readEvents.clear();
    }

    public List<JsonObject> getAllJsonEvents() {
        List<JsonObject> foundEvents = new ArrayList<>();
        while (!readEvents.isEmpty()) {
            JsonObject poll = readEvents.poll();
            if (poll != null) {
                foundEvents.add(poll);
            }
        }

        logger.info("Found {} events", foundEvents.size());

        return foundEvents;
    }

    public <TYPE extends Message> List<TYPE> getEventsOfType(String eventName, Class<TYPE> eventClass) {
        List<TYPE> foundEvents = new ArrayList<>();

        if (eventName != null && eventClass != null) {
            List<JsonObject> capturedEvents = Arrays.asList(readEvents.toArray(new JsonObject[0]));

            for (JsonObject event : capturedEvents) {
                logger.info("Found event: " + event.toString());
                if (EventUtils.getEventName(event).equals(eventName)) {
                    foundEvents.add(ProtobufUtil.jsonToProtobuf(event.toString(), eventClass));
                    readEvents.remove(event);
                }
            }
        } else {
            logger.error("Event name or event class is null");
        }
        return foundEvents;
    }

    public <TYPE extends Message> List<TYPE> getEventsOfType(Class<TYPE> eventClass) {
        List<TYPE> foundEvents = new ArrayList<>();

        if (eventClass != null) {
            List<JsonObject> capturedEvents = Arrays.asList(readEvents.toArray(new JsonObject[0]));

            for (JsonObject event : capturedEvents) {
                logger.info("Found event: " + event.toString());
                if (EventUtils.getEventName(event).equals(eventClass.getSimpleName())) {
                    foundEvents.add(ProtobufUtil.jsonToProtobuf(event.toString(), eventClass));
                    readEvents.remove(event);
                }
            }
        } else {
            logger.error("Event class is null");
        }
        return foundEvents;
    }

    @SuppressWarnings("unchecked")
    public <T>T getEvent(String eventName, Class eventClass, Predicate<T> predicate, long timeout){
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeout){
            //the reversing is for starting with the newest events
            List<T> events = Lists.reverse(getEventsOfType(eventName, eventClass));
            for (T event: events) {
                if(predicate.test(event)){
                    return event;
                }
            }
            new Sleeper().sleepNoException(10);
        }
        logger.error("The event {} is not found after {} ms", eventName, timeout);
        return null;
    }

    public Map<String, Message> getExpectedEvents(Map<String, Class> expectedEvents) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<String, Message>> future = executor.submit(new EventFinder(this, expectedEvents));
        Map<String, Message> foundEvents = new HashMap<>();

        try {
            foundEvents = future.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception ex) {
            logger.error("Could not find expected events: " + expectedEvents.keySet(), ex);
            future.cancel(true);
        }

        executor.shutdownNow();

        // We clear the expected events to be ready for the next testcase
        expectedEvents.clear();

        return foundEvents;
    }
}

