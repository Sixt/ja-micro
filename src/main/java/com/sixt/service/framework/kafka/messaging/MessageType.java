package com.sixt.service.framework.kafka.messaging;

/**
 * Created by abjb on 3/28/17.
 */
public class MessageType {

    private final String type;

    MessageType(String typeName) {
        type = typeName;
    }

    static MessageType of(com.google.protobuf.Message protoMessage) {
        // FIXME define type name!
        return new MessageType(protoMessage.getClass().getCanonicalName());
    }

    @Override
    public String toString() {
        return type;
    }

}
