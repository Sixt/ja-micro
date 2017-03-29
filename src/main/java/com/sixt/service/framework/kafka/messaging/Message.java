package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.protobuf.MessagingEnvelope;


public class Message<T extends com.google.protobuf.Message> {
    private final T payload;
    private final Metadata metadata;

    public Message(T payload, Metadata metadata) {
        this.payload = payload;
        this.metadata = metadata;
    }

    public T getPayload() {
        return null;
    }
    public Metadata getMetadata() {
        return null;
    }
}