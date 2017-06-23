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

package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPublisher.class);

    private final Map<String, String> properties;
    private final String topic;

    private KafkaProducer<String, String> realProducer;

    protected AtomicBoolean isInitialized = new AtomicBoolean(false);

    KafkaPublisher(String topic, Map<String, String> properties) {
        this.topic = topic;
        this.properties = properties;
    }

    public void initialize(String servers) {
        if (isInitialized.get()) {
            logger.warn("Already initialized");
            return;
        }

        Properties props = new Properties();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, SixtPartitioner.class.getName());
        props.put(ProducerConfig.RETRIES_CONFIG, "3");
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        properties.forEach(props::put);

        realProducer = new KafkaProducer<>(props);
        isInitialized.set(true);
    }

    /**
     * Synchronously publish one or more messages with a null partition key
     */
    public boolean publishSync(String... events) {
        if (events != null) {
            return publishEvents(true, null, events);
        } else {
            return true;
        }
    }

    /**
     * Synchronously publish one or more messages with the specified partition key
     */
    public boolean publishSyncWithKey(String key, String... events) {
        if (events != null) {
            return publishEvents(true, key, events);
        } else {
            return true;
        }
    }

    /**
     * Asynchronously publish one or more messages with a null partition key
     */
    public void publishAsync(String... events) {
        if (events != null) {
            publishEvents(false, null, events);
        }
    }

    /**
     * Asynchronously publish one or more messages with a null partition key
     */
    public void publishAsyncWithKey(String key, String... events) {
        if (events != null) {
            publishEvents(false, key, events);
        }
    }

    private boolean publishEvents(boolean sync, String key, String[] events) {
        if (realProducer == null) {
            throw new IllegalStateException("Kafka is null. Was the factory initialized?");
        }
        for (String event : events) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, event);
            try {
                Future<RecordMetadata> future = realProducer.send(record, (metadata, ex) -> {
                    if (ex == null) {
                        logger.debug("Sent message to Kafka: {}", metadata);
                    } else {
                        logger.warn("Send failed for record {}", record, ex);
                    }
                });
                if (sync) {
                    future.get();
                }
            } catch (Exception ex) {
                logger.warn("Publishing event message to Kafka failed", ex);
                return false;
            }
        }
        return true;
    }

    public void shutdown() {
        realProducer.close();
        realProducer = null;
    }
}
