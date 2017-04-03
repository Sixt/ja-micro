package com.sixt.service.framework.kafka.messaging;

import org.apache.kafka.common.TopicPartition;

/**
 * Created by abjb on 3/29/17.
 */
public class PartitionProcessorFactory {

    private TypeDictionary typeDictionary;
    private FailedMessageProcessor failedMessageProcessor;

    public PartitionProcessorFactory(TypeDictionary typeDictionary, FailedMessageProcessor failedMessageProcessor) {
        this.typeDictionary = typeDictionary;
        this.failedMessageProcessor = failedMessageProcessor;
    }

    public PartitionProcessor newProcessorFor(TopicPartition partitionKey) {
        return new PartitionProcessor(partitionKey, typeDictionary, failedMessageProcessor);
    }
}
