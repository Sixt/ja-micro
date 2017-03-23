package com.sixt.service.framework.kafka;

/**
 * Created by abjb on 3/23/17.
 */
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

}