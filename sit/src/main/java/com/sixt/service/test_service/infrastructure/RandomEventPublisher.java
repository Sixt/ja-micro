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
package com.sixt.service.test_service.infrastructure;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.kafka.KafkaPublisher;
import com.sixt.service.framework.kafka.KafkaPublisherFactory;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.test_service.api.TestServiceOuterClass;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class RandomEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RandomEventPublisher.class);

    private final KafkaPublisher publisher;

    @Inject
    public RandomEventPublisher(KafkaPublisherFactory factory) {
        Map<String, String> props = new HashMap<>();
        props.put(ProducerConfig.RETRIES_CONFIG, "3");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        publisher = factory.newBuilder("events", props).build();
    }

    public void publishSync(TestServiceOuterClass.HandlerSuccessEvent event) {
        if (!publisher.publishSync(ProtobufUtil.protobufToJson(event).toString())) {
            logger.warn("Failed to publish event: {}", event);
        }
    }
}
