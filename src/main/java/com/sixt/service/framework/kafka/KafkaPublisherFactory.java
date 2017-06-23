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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

@Singleton
public class KafkaPublisherFactory {

    protected ServiceProperties serviceProperties;

    private Collection<KafkaPublisher> kafkaPublishers = new ConcurrentLinkedQueue<>();

    @Inject
    public KafkaPublisherFactory(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    @Deprecated //no longer needed now that we get populated serviceProperties at guice bootstrap-time
    public void initialize() {
        String servers = serviceProperties.getKafkaServer();
        for (KafkaPublisher publisher : kafkaPublishers) {
            publisher.initialize(servers);
        }
    }

    public KafkaPublisherBuilder newBuilder(String topic) {
        return new KafkaPublisherBuilder(this, topic, Collections.emptyMap());
    }

    public KafkaPublisherBuilder newBuilder(String topic, Map<String, String> properties) {
        return new KafkaPublisherBuilder(this, topic, properties);
    }

    void builtPublisher(KafkaPublisher publisher) {
        kafkaPublishers.add(publisher);
        publisher.initialize(serviceProperties.getKafkaServer());
    }

}
