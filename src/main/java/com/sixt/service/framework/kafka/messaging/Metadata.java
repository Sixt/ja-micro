package com.sixt.service.framework.kafka.messaging;

import com.google.common.base.Strings;
import com.sixt.service.framework.OrangeContext;


// Immutable
public class Metadata {

    // Inbound or outbound message, i.e. did we receive it or is it a newly created one?
    private final boolean wasReceived;

    // Kafka information
    private final Topic topic; // The topic this message was received on (INBOUND) or is to be send to (OUTBOUND).
    private final String partitioningKey; // The key used to determine the partition in the topic.
    private final int partitionId; // The id of the topic partition - only for INBOUND.
    private final long offset; // The offset of the message - only for INBOUND

    // Message headers - see also MessagingEnvelope.proto
    private final String messageId; // FIXME


    // Tracing/correlation ids
    private final String correlationId;  // OPTIONAL. A correlation id to correlate multiple messages, rpc request/responses, etc. belonging to a trace/flow/user request/etc..
    private final String traceId;  // OPTIONAL. Another correlation id for tracing.

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

    public String getTraceId() {
        return traceId;
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
        // FIXME tracing context
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
                ", traceId='" + traceId + '\'' +
                ", requestCorrelationId='" + requestCorrelationId + '\'' +
                ", replyTo=" + replyTo +
                ", type=" + type +
                '}';
    }


    // Object instantiation is done via factory


    Metadata(boolean wasReceived, Topic topic, String partitioningKey, int partitionId, long offset, String messageId, String correlationId, String traceId, String requestCorrelationId, Topic replyTo, MessageType type) {
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
        this.traceId = traceId;

        this.requestCorrelationId = requestCorrelationId;
        this.replyTo = replyTo;

        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        this.type = type;
    }


}
