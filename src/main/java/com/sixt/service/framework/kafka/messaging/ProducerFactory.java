package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.sixt.service.framework.ServiceProperties;
import org.apache.kafka.clients.CommonClientConfigs;

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

        return new Producer(kafkaProducerConfig);
    }

}