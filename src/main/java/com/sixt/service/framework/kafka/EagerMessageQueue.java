package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public class EagerMessageQueue implements MessageQueue {

    private MessageExecutor messageExecutor;

    public EagerMessageQueue(MessageExecutor messageExecutor, long retryDelayMillis) {
        this.messageExecutor = messageExecutor;
    }

    @Override
    public void add(ConsumerRecord<String, String> record) {
        messageExecutor.execute(record);
    }

    @Override
    public void consumed(KafkaTopicInfo topicInfo) {
    }

    @Override
    public void processingEnded(KafkaTopicInfo topicInfo) {
    }
}
