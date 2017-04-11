package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import io.opentracing.Tracer;

public class ConsumerFactory {

    private final ServiceProperties serviceProperties;
    private final TypeDictionary typeDictionary;
    private final Tracer tracer;
    private final MetricBuilderFactory metricBuilderFactory;

    // Stand-alone use without tracing and metrics.
    public ConsumerFactory(ServiceProperties serviceProperties, TypeDictionary typeDictionary) {
        this(serviceProperties, typeDictionary, null, null);
    }

    @Inject
    public ConsumerFactory(ServiceProperties serviceProperties, TypeDictionary typeDictionary, Tracer tracer, MetricBuilderFactory metricBuilderFactory) {
        this.serviceProperties = serviceProperties;
        this.typeDictionary = typeDictionary;
        this.tracer = tracer;
        this.metricBuilderFactory = metricBuilderFactory;
    }

    // Note: There is no default for the FailedMessageProcessor because I want users to explicitly think about error handling.

    // TODO  make group id settable by caller
    // TODO allow caller to specify kafka config (different from default one)

    public Consumer defaultInboxConsumer(FailedMessageProcessor failedMessageStrategy) {
        PartitionProcessorFactory partitionProcessorFactory = new PartitionProcessorFactory(typeDictionary, failedMessageStrategy, tracer, metricBuilderFactory);

        String kafkaBootstrapServers = serviceProperties.getKafkaServer();
        String serviceName = serviceProperties.getServiceName();

        Topic defaultInbox = Topic.defaultServiceInbox(serviceName);
        String consumerGroupId = defaultConsumerGroupId(defaultInbox);

        return new Consumer(defaultInbox, consumerGroupId, kafkaBootstrapServers, partitionProcessorFactory);
    }

    public Consumer consumerForTopic(Topic topic, DiscardFailedMessages failedMessageStrategy) {
        PartitionProcessorFactory partitionProcessorFactory = new PartitionProcessorFactory(typeDictionary, failedMessageStrategy, tracer, metricBuilderFactory);

        String kafkaBootstrapServers = serviceProperties.getKafkaServer();
        String consumerGroupId = defaultConsumerGroupId(topic);

        return new Consumer(topic, consumerGroupId, kafkaBootstrapServers, partitionProcessorFactory);
    }

    private String defaultConsumerGroupId(Topic topic) {
        // default consumer group id consists of topic and service name
        return topic + "-" + serviceProperties.getServiceName();
    }
}
