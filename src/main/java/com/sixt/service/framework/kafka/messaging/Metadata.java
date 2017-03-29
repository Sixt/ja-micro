package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.KafkaTopicInfo;
import com.sixt.service.framework.protobuf.MessagingEnvelope;

/**
 * Created by abjb on 3/23/17.
 */
public class Metadata {

    // Inbound or outbound message, i.e. did we receive it or is it a newly created one?
    private boolean wasReceived;

    // Kafka information
    private Topic topic; // The topic this message was received on (INBOUND) or is to be send to (OUTBOUND).
    private String partitioningKey; // The key used to determine the partition in the topic.
    private int partitionId; // The id of the topic partition - only for INBOUND.
    private long offset; // The offset of the message - only for INBOUND

    // Message headers - see also MessagingEnvelope.proto
    // Tracing/correlation ids
    private String correlationId;  // OPTIONAL. A correlation id to correlate multiple messages, rpc request/responses, etc. belonging to a trace/flow/user request/etc..
    private String traceId;  // OPTIONAL. Another correlation id for tracing.

    // Message exchange patterns
    // -> request/reply
    private String requestCorrelationId; // OPTIONAL. This response correlates to the request with the given id
    private Topic replyTo; // OPTIONAL. Send responses for this request to the given address. See class Topic for syntax.

    private MessageType type; // REQUIRED.

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

}
