package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import org.apache.kafka.common.TopicPartition;

/**
 * Created by abjb on 3/29/17.
 */
public class PartitionProcessorFactory {

    private TypeDictionary typeDictionary;
    private FailedMessageProcessor failedMessageProcessor;


    @Inject
    public PartitionProcessorFactory(TypeDictionary typeDictionary, FailedMessageProcessor failedMessageProcessor) {
        this.typeDictionary = typeDictionary;
        this.failedMessageProcessor = failedMessageProcessor;
    }

    public PartitionProcessor newProcessorFor(TopicPartition partitionKey) {
        return new PartitionProcessor(partitionKey, typeDictionary, failedMessageProcessor);
    }
}
