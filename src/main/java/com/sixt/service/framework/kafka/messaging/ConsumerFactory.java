package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.jcabi.log.Logger;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.kafka.TopicVerification;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import com.sixt.service.framework.util.Sleeper;
import io.opentracing.Tracer;

import static com.google.common.collect.ImmutableSet.of;

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
    // TODO allow poll interval to be settable

    public Consumer defaultInboxConsumer(FailedMessageProcessor failedMessageStrategy) {
        PartitionProcessorFactory partitionProcessorFactory = new PartitionProcessorFactory(typeDictionary, failedMessageStrategy, tracer, metricBuilderFactory);

        String kafkaBootstrapServers = serviceProperties.getKafkaServer();
        String serviceName = serviceProperties.getServiceName();

        Topic defaultInbox = Topic.defaultServiceInbox(serviceName);
        String consumerGroupId = defaultConsumerGroupId(defaultInbox);

        ensureTopicIsPresent(defaultInbox);

        return new Consumer(defaultInbox, consumerGroupId, kafkaBootstrapServers, partitionProcessorFactory);
    }

    public Consumer consumerForTopic(Topic topic, DiscardFailedMessages failedMessageStrategy) {
        PartitionProcessorFactory partitionProcessorFactory = new PartitionProcessorFactory(typeDictionary, failedMessageStrategy, tracer, metricBuilderFactory);

        String kafkaBootstrapServers = serviceProperties.getKafkaServer();
        String consumerGroupId = defaultConsumerGroupId(topic);

        ensureTopicIsPresent(topic);

        return new Consumer(topic, consumerGroupId, kafkaBootstrapServers, partitionProcessorFactory);
    }

    private String defaultConsumerGroupId(Topic topic) {
        // default consumer group id consists of topic and service name
        return topic + "-" + serviceProperties.getServiceName();
    }


    private void ensureTopicIsPresent(Topic topic) {
        TopicVerification verifier = new TopicVerification();
        Sleeper sleeper = new Sleeper();

        // FIXME is is a good idea to ensure the topic here? 
        // FIXME specify the maximum amout of time to wait

        while (!verifier.verifyTopicsExist(serviceProperties.getKafkaServer(), of(topic.toString()), false)) {
            Logger.debug("Verify if topic {} exisits.", topic.toString());
            sleeper.sleepNoException(100);
        }

    }
}
