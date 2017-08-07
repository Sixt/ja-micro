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

package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.sixt.service.framework.ServiceProperties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;

public class ProducerFactory {

    private final ServiceProperties serviceProperties;

    @Inject
    public ProducerFactory(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    public Producer createProducer() {
        String kafkaBootstrapServers = serviceProperties.getKafkaServer();

        Properties kafkaProducerConfig = new Properties();
        kafkaProducerConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        kafkaProducerConfig.put(ProducerConfig.ACKS_CONFIG, "all");  // ensure that records have been replicated to other kafka nodes

        return new Producer(kafkaProducerConfig);
    }

}