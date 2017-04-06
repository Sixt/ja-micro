package com.sixt.service.framework.kafka.messaging;

import com.google.common.base.Strings;
import com.sixt.service.framework.OrangeContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.UUID;

public class Messages {

    private Messages() {
        // Prevent instantiation.
    }

    // Copy-paste is by intention here.

    public static Message oneWayMessage(Topic target, String partitionKey, com.google.protobuf.Message protoPayloadMessage, OrangeContext context) {
        boolean wasReceived = false;

        String messageId = UUID.randomUUID().toString();
        String correlationId = context.getCorrelationId();
        String traceId = ""; // TODO

        Topic replyTo = null; // not required
        String requestCorrelationId = ""; // not required

        MessageType type = MessageType.of(protoPayloadMessage);

        Metadata meta = new Metadata(wasReceived, target, partitionKey, -1, -1, messageId, correlationId, traceId, requestCorrelationId, replyTo, type);
        return new Message(protoPayloadMessage, meta);
    }


    /*
    public static Message requestFor(Topic target, String partitionKey, com.google.protobuf.Message protoPayloadMessage, OrangeContext context) {
        Topic defaultReplyTo = Topic.defaultServiceInbox("FIXME"); // FIXME where to get the service name from? context?
        return requestFor(target, defaultReplyTo, partitionKey,protoPayloadMessage,context);
    }
    */

    public static Message requestFor(Topic target, Topic replyTo, String partitionKey, com.google.protobuf.Message protoPayloadMessage, OrangeContext context) {
        boolean wasReceived = false;

        String messageId = UUID.randomUUID().toString();
        String correlationId = context.getCorrelationId();

        MessageType type = MessageType.of(protoPayloadMessage);


        String traceId = "";

        // Use default inbox for service.
        if (replyTo == null)

        {
            throw new IllegalArgumentException("replyTo required");
        }

        String requestCorrelationId = ""; // not required



        Metadata meta = new Metadata(wasReceived, target, partitionKey, -1, -1, messageId, correlationId, traceId, requestCorrelationId, replyTo, type);
        return new Message(protoPayloadMessage, meta);

    }


    public static Message replyTo(Message originalRequest, com.google.protobuf.Message protoPayloadMessage, OrangeContext context) {
        boolean wasReceived = false;

        // By default, return to sender topic using same partitioning scheme.
        Topic target = originalRequest.getMetadata().getReplyTo();
        String partitionKey = originalRequest.getMetadata().getPartitioningKey();

        String messageId = UUID.randomUUID().toString();
        String correlationId = context.getCorrelationId();
        String traceId = ""; // TODO

        String requestCorrelationId = originalRequest.getMetadata().getMessageId();
        Topic replyTo = null; // not required

        MessageType type = MessageType.of(protoPayloadMessage);

        Metadata meta = new Metadata(wasReceived, target, partitionKey, -1, -1, messageId, correlationId, traceId, requestCorrelationId, replyTo, type);
        return new Message(protoPayloadMessage, meta);
    }


    static Message fromKafka(com.google.protobuf.Message protoMessage, Envelope envelope, ConsumerRecord<String, byte[]> record) {
        boolean wasReceived = true;

        Topic topic = new Topic(record.topic());
        String partitioningKey = record.key();
        int partitionId = record.partition();
        long offset = record.offset();

        String messageId = envelope.getMessageId();
        String correlationId = envelope.getCorrelationId();

        MessageType type = MessageType.of(protoMessage);

        String traceId = envelope.getTraceId();


        String requestCorrelationId = envelope.getRequestCorrelationId();
        Topic replyTo = new Topic(envelope.getReplyTo());



        Metadata meta = new Metadata(wasReceived, topic, partitioningKey, partitionId, offset, messageId, correlationId, traceId, requestCorrelationId, replyTo, type);
        return new Message(protoMessage, meta);
    }


    static Envelope toKafka(Message message) {
        Envelope.Builder envelope = Envelope.newBuilder();
        Metadata meta = message.getMetadata();

        envelope.setMessageId(meta.getMessageId());

        // Correlation ids are set when building the message
        if (!Strings.isNullOrEmpty(meta.getCorrelationId())) {
            envelope.setCorrelationId(meta.getCorrelationId());
        }
        if (!Strings.isNullOrEmpty(meta.getTraceId())) {
            envelope.setCorrelationId(meta.getTraceId());
        }

        // Message exchange pattern headers
        if (meta.getReplyTo() != null) {
            envelope.setReplyTo(meta.getReplyTo().toString());
        }
        if (!Strings.isNullOrEmpty(meta.getRequestCorrelationId())) {
            envelope.setRequestCorrelationId(meta.getRequestCorrelationId());
        }

        // Payload (mandatory fields!)
        envelope.setMessageType(meta.getType().toString());
        envelope.setInnerMessage(message.getPayload().toByteString()); // Serialize the proto payload to bytes

        return envelope.build();
    }
}
