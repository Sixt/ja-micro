package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

public class KafkaPublisherTest {

    private KafkaPublisher publisher = new KafkaPublisher("events", null);

    @Test
    public void sendFailsReturnsFalse() {
        KafkaProducer producer = mock(KafkaProducer.class);
        publisher.realProducer = producer;
        RecordMetadata metadata = new RecordMetadata(null, 0, 0,
                0, Long.valueOf(0), 0, 0);
        ArgumentCaptor<Callback> captor = ArgumentCaptor.forClass(Callback.class);
        when(producer.send(any(), captor.capture())).then(
            invocation -> {
                captor.getValue().onCompletion(metadata, new TimeoutException("error"));
                return new CompletableFuture();
            });
        String[] events = { "test" };
        assertThat(publisher.publishEvents(false, null, events)).isFalse();
    }

}
