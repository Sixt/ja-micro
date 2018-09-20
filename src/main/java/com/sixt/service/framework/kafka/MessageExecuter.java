package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface MessageExecuter {
    void execute(ConsumerRecord<String, String> record);
}
