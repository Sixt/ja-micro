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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.FeatureFlags;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

@Singleton
public class KafkaPublisherFactory {

    protected final ServiceProperties serviceProperties;
    private Collection<KafkaPublisher> kafkaPublishers = new ConcurrentLinkedQueue<>();
    private final MetricBuilderFactory metricBuilderFactory;

    @Inject
    public KafkaPublisherFactory(ServiceProperties serviceProperties, MetricBuilderFactory metricBuilderFactory) {
        this.serviceProperties = serviceProperties;
        this.metricBuilderFactory = metricBuilderFactory;
    }

    @Deprecated //no longer needed now that we get populated serviceProperties at guice bootstrap-time
    public void initialize() {
        String servers = serviceProperties.getKafkaServer();
        for (KafkaPublisher publisher : kafkaPublishers) {
            publisher.initialize(servers);
        }
    }

    public Map<String, String> getDefaultProperties() {
        Map<String, String> retval = new HashMap<>();
        retval.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Integer.toString(FeatureFlags.getKafkaRequestTimeoutMs(serviceProperties)));
        retval.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, Integer.toString(FeatureFlags.getKafkaMaxBlockMs(serviceProperties)));
        retval.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        retval.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        retval.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, SixtPartitioner.class.getName());
        retval.put(ProducerConfig.RETRIES_CONFIG, "3");
        retval.put(ProducerConfig.ACKS_CONFIG, "all");
        return retval;
    }

    public KafkaPublisherBuilder newBuilder(String topic) {
        return newBuilder(topic, getDefaultProperties());
    }

    public KafkaPublisherBuilder newBuilder(String topic, Map<String, String> properties) {
        KafkaPublisherBuilder builder = new KafkaPublisherBuilder(this, topic, properties);
        builder.setMetricBuilderFactory(metricBuilderFactory);
        return builder;
    }

    void builtPublisher(KafkaPublisher publisher) {
        kafkaPublishers.add(publisher);
        publisher.initialize(serviceProperties.getKafkaServer());
    }

}
