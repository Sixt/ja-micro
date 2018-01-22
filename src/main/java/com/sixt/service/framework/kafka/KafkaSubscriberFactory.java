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
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.MetricBuilderFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

@Singleton
public class KafkaSubscriberFactory<TYPE> {

    protected ServiceProperties serviceProperties;
    protected Collection<KafkaSubscriber<TYPE>> kafkaSubscribers = new ConcurrentLinkedQueue<>();
    private final MetricBuilderFactory metricBuilderFactory;

    @Inject
    public KafkaSubscriberFactory(ServiceProperties serviceProperties, MetricBuilderFactory metricBuilderFactory) {
        this.serviceProperties = serviceProperties;
        this.metricBuilderFactory = metricBuilderFactory;
    }

    /**
     * ONLY use this variant for unit tests!
     */
    @Deprecated
    public KafkaSubscriberFactory(ServiceProperties serviceProperties) {
        this(serviceProperties, null);
    }

    @Deprecated //no longer needed now that we get populated serviceProperties at guice bootstrap-time
    public void initialize() {
        String servers = serviceProperties.getKafkaServer();
        for (KafkaSubscriber subscriber : kafkaSubscribers) {
            subscriber.initialize(servers);
        }
    }

    public KafkaSubscriberBuilder<TYPE> newBuilder(String topic, EventReceivedCallback<TYPE> callback) {
        KafkaSubscriberBuilder<TYPE> retval = new KafkaSubscriberBuilder<>(this, topic, callback);
        retval.setMetricBuilderFactory(metricBuilderFactory);
        return retval;
    }

    public void builtSubscriber(KafkaSubscriber<TYPE> subscriber) {
        kafkaSubscribers.add(subscriber);
        subscriber.initialize(serviceProperties.getKafkaServer());
    }

}
