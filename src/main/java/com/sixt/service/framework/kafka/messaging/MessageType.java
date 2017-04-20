/**
 * Copyright 2016-2017 Sixt GmbH & Co. Autovermietung KG
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.sixt.service.framework.kafka.messaging;

import java.lang.reflect.Type;

/**
 * Value object to represent the type of a (protobuf) message.
 * Used to specify the type of the inner message in the messaging envelope.
 * <p>
 * Conventions:
 * - The type name is the Java Type.getTypeName() of the generated protobuf message.
 * <p>
 * - In the defining proto file
 * -- specify option java_multiple_files = true; to avoid the OuterClass that nests the message types.
 * -- use package com.sixt.service.{SERVICENAME}.api; as default namespace/package for the asynchronous messaging contract
 * -- there is no need for the option java_package directive
 */
public final class MessageType {

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
