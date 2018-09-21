package com.sixt.service.framework.kafka;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class QueueTypeEnumTest {

    @Test
    public void getMessageQueueInstance_succes() {
        assertThat(KafkaSubscriber.QueueType.Priority.getMessageQueueInstance(mock(MessageExecutor.class), 100))
                .isInstanceOf(PriorityMessageQueue.class);
        assertThat(KafkaSubscriber.QueueType.Eager.getMessageQueueInstance(mock(MessageExecutor.class), 100))
                .isInstanceOf(EagerMessageQueue.class);
    }
}
