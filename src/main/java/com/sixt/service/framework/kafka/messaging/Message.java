package com.sixt.service.framework.kafka.messaging;

public class Message<T extends com.google.protobuf.Message> {
    private final T payload;
    private final Metadata metadata;

    public Message(T payload, Metadata metadata) {
        this.payload = payload;
        this.metadata = metadata;
    }

    public T getPayload() {
        return payload;
    }

    public Metadata getMetadata() {
        return metadata;
    }
}