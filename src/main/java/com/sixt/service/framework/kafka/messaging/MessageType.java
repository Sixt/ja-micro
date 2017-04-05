package com.sixt.service.framework.kafka.messaging;

import java.lang.reflect.Type;

/**
 * Value object to represent the type of a (protobuf) message.
 * Used to specify the type of the inner message in the messaging envelope.
 *
 * Conventions:
 * - The type name is the Java Type.getTypeName() of the generated protobuf message.
 *
 * - In the defining proto file
 * -- specify option java_multiple_files = true; to avoid the OuterClass that nests the message types.
 * -- use package com.sixt.service.{SERVICENAME}.api; as default namespace/package for the asynchronous messaging contract
 * -- there is no need for the option java_package directive
 */
public class MessageType {

    private final String type;

    MessageType(String typeName) {
        type = typeName;
    }

    static MessageType of(com.google.protobuf.Message protoMessage) {
        return new MessageType(protoMessage.getClass().getTypeName());
    }

    @Override
    public String toString() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageType that = (MessageType) o;

        return type != null ? type.equals(that.type) : that.type == null;
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }

    public static MessageType of(Type t) {
        return new MessageType(t.getTypeName());
    }
}
