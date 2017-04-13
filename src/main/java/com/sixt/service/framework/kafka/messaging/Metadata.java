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

import com.google.common.base.Strings;
import com.sixt.service.framework.OrangeContext;
import org.slf4j.Marker;

import static net.logstash.logback.marker.Markers.append;

/**
 * Metadata information about the Message such as
 * - If it was received from Kafka or is a newly created mesage.
 * - Kafka related information such as topic, partition id, partitioning key and the offset of the message in the partition.
 * - Header fields sent with the Message (in the Envolope), e.g. message id, type of the inner message, correlation ids, etc.
 *
 * Depending on the message exchange pattern, some fields are optional.
 *
 * For request-response, the request requires to have the reply-to topic set. The consumer of the request must send the response
 * back to the reply-to topic. In the response, the requestCorrelationId is required and refers to the message id of the original request.
 *
 */
public class Metadata {
    // Immutable

    // Keep in sync with Messaging.proto / message Envelope

    // Inbound or outbound message, i.e. did we receive it or is it a newly created one?
    private final boolean wasReceived;

    // Kafka information
    private final Topic topic; // The topic this message was received on (INBOUND) or is to be send to (OUTBOUND).
    private final String partitioningKey; // The key used to determine the partition in the topic.
    private final int partitionId; // The id of the topic partition - only for INBOUND.
    private final long offset; // The offset of the message - only for INBOUND

    // Message headers - see also Messaging.proto
    private final String messageId;


    // Tracing/correlation ids
    private final String correlationId;  // OPTIONAL. A correlation id to correlate multiple messages, rpc request/responses, etc. belonging to a trace/flow/user request/etc..

    // Message exchange patterns
    // -> request/reply
    private final String requestCorrelationId; // REQUIRED for RESPONSE. This response correlates to the message id of the original request.
    private final Topic replyTo; // REQUIRED for REQUEST. Send responses for this request to the given address. See class Topic for syntax.

    private final MessageType type; // REQUIRED.

    public boolean isInbound() {
        return wasReceived();
    }

    public boolean isOutbound() {
        return !isInbound();
    }

    public boolean wasReceived() {
        return wasReceived;
    }

    public Topic getTopic() {
        return topic;
    }

    public String getPartitioningKey() {
        return partitioningKey;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public long getOffset() {
        return offset;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getRequestCorrelationId() {
        return requestCorrelationId;
    }

    public Topic getReplyTo() {
        return replyTo;
    }

    public MessageType getType() {
        return type;
    }


    // Helper methods --------------------------

    public OrangeContext newContextFromMetadata() {
        return new OrangeContext(correlationId);
    }

    @Override
    public String toString() {
        return "Metadata{" +
                "wasReceived=" + wasReceived +
                ", topic=" + topic +
                ", partitioningKey='" + partitioningKey + '\'' +
                ", partitionId=" + partitionId +
                ", offset=" + offset +
                ", messageId='" + messageId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", requestCorrelationId='" + requestCorrelationId + '\'' +
                ", replyTo=" + replyTo +
                ", type=" + type +
                '}';
    }

    public Marker getLoggingMarker() {
        // If we get more optional header fields, we should probably exclude them if they are empty.
        Marker messageMarker = append("topic", topic)
                .and(append("partitionId", partitionId))
                .and(append("partitioningKey", partitioningKey))
                .and(append("offset", offset))
                .and(append("messageId", messageId))
                .and(append("correlationId", correlationId))
                .and(append("requestCorrelationId", requestCorrelationId))
                .and(append("replyTo", replyTo))
                .and(append("messageType", type))
                ;

        return messageMarker;
    }


    // Object instantiation is done via factory
    Metadata(boolean wasReceived, Topic topic, String partitioningKey, int partitionId, long offset, String messageId, String correlationId, String requestCorrelationId, Topic replyTo, MessageType type) {
        this.wasReceived = wasReceived;

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("topic is required");
        }
        this.topic = topic;

        if (Strings.isNullOrEmpty(partitioningKey)) {
            throw new IllegalArgumentException("non-empty partitioningKey is required");
        }
        this.partitioningKey = partitioningKey;

        this.partitionId = partitionId;
        this.offset = offset;

        if (Strings.isNullOrEmpty(messageId)) {
            throw new IllegalArgumentException("non-empty messageId is required");
        }
        this.messageId = messageId;

        this.correlationId = correlationId;

        this.requestCorrelationId = requestCorrelationId;
        this.replyTo = replyTo;

        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        this.type = type;
    }
}
