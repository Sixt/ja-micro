package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public class EagerMessageQueue implements MessageQueue {

    private MessageExecuter messageExecuter;

    public EagerMessageQueue(MessageExecuter messageExecuter, long retryDelayMillis) {
        this.messageExecuter = messageExecuter;
    }

    @Override
    public void add(ConsumerRecord<String, String> record) {
        messageExecuter.execute(record);
    }

    @Override
    public void consumed(KafkaTopicInfo topicInfo) {
    }

    @Override
    public void processingEnded(KafkaTopicInfo topicInfo) {
    }
}
