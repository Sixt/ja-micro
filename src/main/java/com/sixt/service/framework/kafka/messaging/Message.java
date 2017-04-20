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

public final class Message<T extends com.google.protobuf.Message> {
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

    @Override
    public String toString() {
        return "Message{" +
                "payload=" + payload +
                ", metadata=" + metadata +
                '}';
    }
}