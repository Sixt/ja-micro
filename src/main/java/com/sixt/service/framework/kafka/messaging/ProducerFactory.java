package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.sixt.service.framework.ServiceProperties;

public class ProducerFactory {

    private final ServiceProperties serviceProperties;

    @Inject
    public ProducerFactory(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    // TODO May want to be able to override default Kakfa config.

    @Provides
    public Producer createProducer() {
        String kafkaBootstrapServers = serviceProperties.getKafkaServer();
        return new Producer(kafkaBootstrapServers);
    }

}