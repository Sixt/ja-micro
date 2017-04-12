package com.sixt.service.framework.kafka.messaging;

import com.google.common.base.Strings;
import com.sixt.service.framework.OrangeContext;
import org.slf4j.Marker;

import static net.logstash.logback.marker.Markers.append;


// Immutable
public class Metadata {

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
    private final String requestCorrelationId; // OPTIONAL. This response correlates to the request with the given id
    private final Topic replyTo; // OPTIONAL. Send responses for this request to the given address. See class Topic for syntax.

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
