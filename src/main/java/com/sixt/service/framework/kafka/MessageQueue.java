package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface MessageQueue {
    void add(ConsumerRecord<String, String> record);
    void consumed(KafkaTopicInfo topicInfo);
    void processingEnded(KafkaTopicInfo topicInfo);
}
