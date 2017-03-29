package com.sixt.service.framework.kafka.messaging;


import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.sixt.service.framework.protobuf.MessagingEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class MessageDispatcher {

    private final TypeDictionary typeDictionary;
    private final PartitionProcessor unhandledMessages;
    private final ThreadPoolExecutor executor;
    private final FailedMessageProcessor failedMessageProcessor;

    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    MessageDispatcher() {
        // FIXME
        this.typeDictionary = null;
        this.unhandledMessages = null;
        this.failedMessageProcessor = null;

        // Single threaded execution per partition to preserve ordering guarantees.
        // EXTENSION: if required, allow multiple threads sacrificing ordering.
        executor = new ThreadPoolExecutor(1, 1, 24, TimeUnit.HOURS, unhandledMessages.getQueue());
    }


    public void stopProcessing() {

        // We mark this dispatcher as stopped, so no new tasks will execute.
        isStopped.set(true);
    }

    public void awaitTermination(long n, TimeUnit unit) {
        try {
            executor.awaitTermination(n, unit);
        } catch (InterruptedException e) {
            // FIXME
        }
    }

    public DeliveryTask taskFor(ConsumerRecord<String, byte[]> record) {
        return new DeliveryTask(record);
    }

    class DeliveryTask implements Runnable {

        private final ConsumerRecord<String, byte[]> record;
        private Message message;

        public DeliveryTask(ConsumerRecord<String, byte[]> record) {
            this.record = record;
        }

        @Override
        public void run() {
            if (isStopped.get()) {
                return;
            }

            parseMessage();
            executeHander();
        }

        private void parseMessage() {
            try {
                MessagingEnvelope envelope = MessagingEnvelope.parseFrom(record.value());

                MessageType type = new MessageType(envelope.getMessageType());
                Parser<com.google.protobuf.Message> parser = typeDictionary.parserFor(type);

                com.google.protobuf.Message innerMessage = parser.parseFrom(envelope.getInnerMessage());

                // FIXME fill them
                message = Message.fromKafka(innerMessage, envelope);

            } catch (InvalidProtocolBufferException e) {
                // Cannot even unmarshal the envelope
                // FIXME!!
            }
        }

        private void executeHander() {
            try {
                MessageHandler handler = typeDictionary.messageHandlerFor(message.getMetadata().getType());

                // Leave ja-micro: hand over execution to service-specific handler.
                handler.onMessage(message, message.getMetadata().getContext());

            } catch (Throwable error) {
                // TODO distinguish between no handler and any other problem
                // TODO we need something like a circuit breaker that pauses message delivery if we have high failure rate

                // Handlers are responsible to handle expected exceptions (such as domain logic failures) and re-try temporary failures.
                // If we get an exception here it's either a unrecoverable condition (e.g. database not available) or a lazy developer.
                failedMessageProcessor.onFailedMessage(message, error);

            } finally {
                // All messages including the failed ones processed by a handler are marked as consumed and committed to Kafka.
                // TODO have some way for the handler to do a early commit
                unhandledMessages.markAsConsumed(message.getMetadata().getTopicInfo().getOffset());
            }
        }
    }


}
