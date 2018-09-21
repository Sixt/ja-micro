package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class EagerMessageQueueTest {

    private MessageExecutor messageExecutor;
    private MessageQueue messageQueue;
    private String topic = "topic";
    private String defaultKey = "key";
    private String defaultValue = "value";

    @Before
    public void setup() {
        messageExecutor = mock(MessageExecutor.class);
        messageQueue = new EagerMessageQueue(messageExecutor, 5000);
    }

    @Test
    public void queue_addTwoRecord_allExecuted() {
        ConsumerRecord record1 = new ConsumerRecord<>(topic, 0, 0, defaultKey, defaultValue);
        messageQueue.add(record1);
        ConsumerRecord record2 = new ConsumerRecord<>(topic, 0, 0, defaultKey, defaultValue);
        messageQueue.add(record2);

        ArgumentCaptor<ConsumerRecord> captor = ArgumentCaptor.forClass(ConsumerRecord.class);
        verify(messageExecutor, times(2)).execute(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(record1);
        assertThat(captor.getAllValues().get(0)).isEqualTo(record2);
    }

}
