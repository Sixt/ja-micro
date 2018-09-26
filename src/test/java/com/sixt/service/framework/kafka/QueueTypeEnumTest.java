package com.sixt.service.framework.kafka;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class QueueTypeEnumTest {

    @Test
    public void getMessageQueueInstance_succes() {
        assertThat(KafkaSubscriber.QueueType.OffsetBlocking.getMessageQueueInstance(mock(MessageExecutor.class), 100))
                .isInstanceOf(OffsetBlockingMessageQueue.class);
        assertThat(KafkaSubscriber.QueueType.Eager.getMessageQueueInstance(mock(MessageExecutor.class), 100))
                .isInstanceOf(EagerMessageQueue.class);
    }
}
