package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import io.opentracing.Tracer;

import java.util.Properties;


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

    // Design note: There is no default for the FailedMessageProcessor because I want users to explicitly think about error handling.

    public Consumer defaultInboxConsumer(FailedMessageProcessor failedMessageStrategy) {
        String serviceName = serviceProperties.getServiceName();
        Topic defaultInbox = Topic.defaultServiceInbox(serviceName);
        String consumerGroupId = defaultConsumerGroupId(defaultInbox);

        return new Consumer(defaultInbox, consumerGroupId, defaultKafkaConfig(), defaultPartitionProcessorFactory(failedMessageStrategy));
    }

    public Consumer consumerForTopic(Topic topic, DiscardFailedMessages failedMessageStrategy) {
        String consumerGroupId = defaultConsumerGroupId(topic);

        return new Consumer(topic, consumerGroupId, defaultKafkaConfig(), defaultPartitionProcessorFactory(failedMessageStrategy));
    }

    private String defaultConsumerGroupId(Topic topic) {
        // default consumer group id consists of topic and service name
        return topic + "-" + serviceProperties.getServiceName();
    }

    private Properties defaultKafkaConfig() {
        String kafkaBootstrapServers = serviceProperties.getKafkaServer();

        Properties kafkaConfig = new Properties();
        kafkaConfig.put("bootstrap.servers", kafkaBootstrapServers);

        // The heartbeat is send in the background by the client library itself
        kafkaConfig.put("heartbeat.interval.ms", "10000");
        kafkaConfig.put("session.timeout.ms", "30000");

        // Require explicit commit handling.
        kafkaConfig.put("enable.auto.commit", "false");

        // If this is a new group, start reading the topic from the beginning.
        kafkaConfig.put("auto.offset.reset", "earliest");

        // This is the actual timeout for the consumer loop thread calling poll() before Kafka rebalances the group.
        kafkaConfig.put("max.poll.interval.ms", 10000);

        return kafkaConfig;
    }

    private PartitionProcessorFactory defaultPartitionProcessorFactory(FailedMessageProcessor failedMessageStrategy) {
        PartitionProcessorFactory partitionProcessorFactory = new PartitionProcessorFactory(typeDictionary, failedMessageStrategy, tracer, metricBuilderFactory);
        return partitionProcessorFactory;
    }

}
