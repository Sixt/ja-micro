package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.sixt.service.framework.ServiceProperties;

/**
 * Created by abjb on 4/5/17.
 */
public class ConsumerFactory {

    private final ServiceProperties serviceProperties;
    private final TypeDictionary typeDictionary;

    @Inject
    public ConsumerFactory(ServiceProperties serviceProperties, TypeDictionary typeDictionary) {
        this.serviceProperties = serviceProperties;
        this.typeDictionary = typeDictionary;
    }

    // There is no default for the FailedMessageProcessor because I want users to explicitly think about error handling.

    // TODO  make group id settable by caller
    // TODO allow caller to specify kafka config (different from default one)

    public Consumer defaultInboxConsumer(FailedMessageProcessor failedMessageStrategy) {
        PartitionProcessorFactory partitionProcessorFactory = new PartitionProcessorFactory(typeDictionary, failedMessageStrategy);

        String kafkaBootstrapServers = serviceProperties.getKafkaServer();
        String serviceName = serviceProperties.getServiceName();

        Topic defaultInbox = Topic.defaultServiceInbox(serviceName);
        String consumerGroupId = defaultConsumerGroupId(defaultInbox);

        return new Consumer(defaultInbox, consumerGroupId, kafkaBootstrapServers, partitionProcessorFactory);
    }

    public Consumer consumerForTopic(Topic topic, DiscardFailedMessages failedMessageStrategy) {
        PartitionProcessorFactory partitionProcessorFactory = new PartitionProcessorFactory(typeDictionary, failedMessageStrategy);

        String kafkaBootstrapServers = serviceProperties.getKafkaServer();
        String consumerGroupId = defaultConsumerGroupId(topic);

        return new Consumer(topic, consumerGroupId, kafkaBootstrapServers, partitionProcessorFactory);
    }

    private String defaultConsumerGroupId(Topic topic) {
        // default consumer group id consists of topic and service name
        return topic + "-" + serviceProperties.getServiceName();
    }
}
