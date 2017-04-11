package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.metrics.MetricBuilderFactory;
import io.opentracing.Tracer;
import org.apache.kafka.common.TopicPartition;

class PartitionProcessorFactory {
    private final TypeDictionary typeDictionary;
    private final FailedMessageProcessor failedMessageProcessor;
    private final Tracer tracer;
    private final MetricBuilderFactory metricBuilderFactory;

    PartitionProcessorFactory(TypeDictionary typeDictionary, FailedMessageProcessor failedMessageProcessor, Tracer tracer, MetricBuilderFactory metricsBuilderFactory) {
        this.typeDictionary = typeDictionary;
        this.failedMessageProcessor = failedMessageProcessor;
        this.tracer = tracer;
        this.metricBuilderFactory = metricsBuilderFactory;
    }

    PartitionProcessor newProcessorFor(TopicPartition partitionKey) {
        return new PartitionProcessor(partitionKey, typeDictionary, failedMessageProcessor, tracer, metricBuilderFactory);
    }
}
