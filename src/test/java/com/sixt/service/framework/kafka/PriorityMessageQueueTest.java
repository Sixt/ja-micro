package com.sixt.service.framework.kafka;

import com.sixt.service.framework.util.Sleeper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PriorityMessageQueueTest {

    private MessageExecutor messageExecutor;
    private MessageQueue messageQueue;
    private String topic = "topic";
    private String defaultKey = "key";
    private String defaultValue = "value";

    @Before
    public void setup() {
        messageExecutor = mock(MessageExecutor.class);
        messageQueue = new PriorityMessageQueue(messageExecutor, 5000);
    }

    @Test
    public void queue_addRecord_execeuted() {
        ConsumerRecord record = new ConsumerRecord<>(topic, 0, 0, defaultKey, defaultValue);
        messageQueue.add(record);

        ArgumentCaptor<ConsumerRecord> captor = ArgumentCaptor.forClass(ConsumerRecord.class);
        verify(messageExecutor).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo(record);
    }

    @Test
    public void queue_addRecordsInOnePartition_firstExecuted() {
        ConsumerRecord record1 = new ConsumerRecord<>(topic, 0, 0, defaultKey, defaultValue);
        messageQueue.add(record1);
        messageQueue.add(new ConsumerRecord<>(topic, 0, 1, defaultKey, defaultValue));

        ArgumentCaptor<ConsumerRecord> captor = ArgumentCaptor.forClass(ConsumerRecord.class);
        verify(messageExecutor).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo(record1);
    }

    @Test
    public void queue_addRecordsInTwoPartition_bothExecuted() {
        ConsumerRecord record1 = new ConsumerRecord<>(topic, 0, 0, defaultKey, defaultValue);
        messageQueue.add(record1);
        messageQueue.add(new ConsumerRecord<>(topic, 0, 1, defaultKey, defaultValue));
        messageQueue.add(new ConsumerRecord<>(topic, 0, 2, defaultKey, defaultValue));
        ConsumerRecord record2 = new ConsumerRecord<>(topic, 1, 1, defaultKey, defaultValue);
        messageQueue.add(record2);
        messageQueue.add(new ConsumerRecord<>(topic, 1, 1, defaultKey, defaultValue));
        messageQueue.add(new ConsumerRecord<>(topic, 1, 2, defaultKey, defaultValue));

        ArgumentCaptor<ConsumerRecord> captor = ArgumentCaptor.forClass(ConsumerRecord.class);
        verify(messageExecutor, times(2)).execute(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(record1);
        assertThat(captor.getAllValues().get(1)).isEqualTo(record2);
    }

    @Test
    public void queue_consumeRecord_nextExecuted() {
        ConsumerRecord record1 = new ConsumerRecord<>(topic, 0, 0, defaultKey, defaultValue);
        messageQueue.add(record1);
        ConsumerRecord record2 = new ConsumerRecord<>(topic, 0, 1, defaultKey, defaultValue);
        messageQueue.add(record2);
        ConsumerRecord record3 = new ConsumerRecord<>(topic, 0, 2, defaultKey, defaultValue);
        messageQueue.add(record3);

        messageQueue.consumed(new KafkaTopicInfo(topic, 0, 0, defaultKey));
        messageQueue.consumed(new KafkaTopicInfo(topic, 0, 1, defaultKey));
        messageQueue.consumed(new KafkaTopicInfo(topic, 0, 2, defaultKey));

        ArgumentCaptor<ConsumerRecord> captor = ArgumentCaptor.forClass(ConsumerRecord.class);
        verify(messageExecutor, times(3)).execute(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(record1);
        assertThat(captor.getAllValues().get(1)).isEqualTo(record2);
        assertThat(captor.getAllValues().get(2)).isEqualTo(record3);
    }

    @Test
    public void queue_processingEnded_retryScheduled() {
        messageQueue = new PriorityMessageQueue(messageExecutor, 100);
        ConsumerRecord record1 = new ConsumerRecord<>(topic, 0, 0, defaultKey, defaultValue);
        messageQueue.add(record1);
        messageQueue.add(new ConsumerRecord<>(topic, 0, 1, defaultKey, defaultValue));
        messageQueue.add(new ConsumerRecord<>(topic, 0, 2, defaultKey, defaultValue));

        messageQueue.processingEnded(new KafkaTopicInfo(topic, 0, 0, defaultKey));
        new Sleeper().sleepNoException(120);
        messageQueue.processingEnded(new KafkaTopicInfo(topic, 0, 0, defaultKey));
        new Sleeper().sleepNoException(120);

        ArgumentCaptor<ConsumerRecord> captor = ArgumentCaptor.forClass(ConsumerRecord.class);
        verify(messageExecutor, times(3)).execute(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(record1);
        assertThat(captor.getAllValues().get(1)).isEqualTo(record1);
        assertThat(captor.getAllValues().get(2)).isEqualTo(record1);
    }

}
