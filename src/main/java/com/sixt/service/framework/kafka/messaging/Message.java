package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.protobuf.MessagingEnvelope;


public class Message<T extends com.google.protobuf.Message> {

    public T getMessage() {
        return null;
    }

    public MessageMetadata getMetadata() {
        return null;
    }

    // TODO proper builder methods
    public Message with(com.google.protobuf.Message payload) {
        return this;
    }

    public static Message replyTo(Message request) {
        return null;
    }

    static Message fromKafka(com.google.protobuf.Message payload, MessagingEnvelope envelope) {
        // FIXME
        return new Message();
    }

}