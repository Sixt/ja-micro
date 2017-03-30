package com.sixt.service.framework.kafka.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.sixt.service.framework.protobuf.MessagingEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


class PartitionProcessor {
    private static final Logger logger = LoggerFactory.getLogger(PartitionProcessor.class);

    private static final int MAX_MESSAGES_IN_FLIGHT = 100;

    // The partition processor is a queue plus a worker thread.
    private final BlockingQueue<Runnable> undeliveredMessages;
    private final ThreadPoolExecutor executor;

    // Which partition is this processor responsible for?
    private final TopicPartition partitionKey;

    // Injected
    private final TypeDictionary typeDictionary;
    private final FailedMessageProcessor failedMessageProcessor;

    // Lifecycle state
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final AtomicBoolean isTerminated = new AtomicBoolean(false);

    // Offset/commit handling
    private AtomicLong lastConsumedOffset = new AtomicLong(-2); // i.e. unknown
    private AtomicLong lastComittedOffset = new AtomicLong(-1); // i.e. unknown


    // Lifecycle --------------------------------------------------

    // TODO injection
    PartitionProcessor(TopicPartition partitionKey, TypeDictionary typeDictionary, FailedMessageProcessor failedMessageProcessor) {
        this.partitionKey = partitionKey;
        this.typeDictionary = typeDictionary;
        this.failedMessageProcessor = failedMessageProcessor;

        undeliveredMessages = new LinkedBlockingQueue<>();

        // Single threaded execution per partition to preserve ordering guarantees.
        // EXTENSION:
        // - if required, allow multiple threads sacrificing ordering.
        // - but then the commmit offset handling requires more thoughts
        executor = new ThreadPoolExecutor(1, 1, 24, TimeUnit.HOURS, undeliveredMessages);
    }

    public void stopProcessing() {
        // We mark this dispatcher as stopped, so no new tasks will execute.
        isStopped.set(true);
        executor.shutdown();
    }

    public boolean isTerminated() {
        return isTerminated.get();
    }

    public void waitForHandlersToTerminate(long timeoutMillis) {
        stopProcessing(); // ensure that we're shutting down

        try {

            boolean terminatedSuccessfully = executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);

            if (!terminatedSuccessfully) {
                logger.warn("PartitionProcessor {}: still running message handlers after waiting {} ms to terminate.", partitionKey, timeoutMillis);
            }

            isTerminated.set(true);

        } catch (InterruptedException e) {
            logger.warn("PartitionProcessor {}: Interrupted while waiting to terminate.", partitionKey);
        }
    }


    // Message dispatch --------------------------------------------------

    public void enqueue(ConsumerRecord<String, byte[]> record) {
        if (isStopped.get()) {
            logger.info("Ignored records to be enqueued after PartitionProcessor {} was stopped.", partitionKey);
            return;
        }

        executor.submit(new MessageDeliveryTask(record));
    }


    class MessageDeliveryTask implements Runnable {

        private final ConsumerRecord<String, byte[]> record;
        private Message message;

        public MessageDeliveryTask(ConsumerRecord<String, byte[]> record) {
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
                message = Messages.fromKafka(innerMessage, envelope, record);

            } catch (InvalidProtocolBufferException e) {
                // Cannot even unmarshal the envelope
                // FIXME!!
            } catch (Throwable t) {
                logger.error("WHAAAT?", t);
            }
        }


        private void executeHander() {
            try {
                MessageHandler handler = typeDictionary.messageHandlerFor(message.getMetadata().getType());

                // Leave the framework here: hand over execution to service-specific handler.
                handler.onMessage(message, message.getMetadata().newContextFromMetadata());

            } catch (Throwable error) {
                // TODO distinguish between no handler and any other problem
                // TODO we need something like a circuit breaker that pauses message delivery if we have high failure rate

                // Handlers are responsible to handle expected exceptions (such as domain logic failures) and re-try temporary failures.
                // If we get an exception here it's either a unrecoverable condition (e.g. database not available) or a lazy developer.
                failedMessageProcessor.onFailedMessage(message, error);

            } finally {
                // All messages including the failed ones processed by a handler are marked as consumed and committed to Kafka.
                // TODO have some way for the handler to do a early commit
                markAsConsumed(message.getMetadata().getOffset());
            }
        }
    }

    // Offset / commit handling --------------------------------------------------

    int numberOfUnprocessedMessages() {
        // Thread safety: snapshot value
        return undeliveredMessages.size();
    }

    void markAsConsumed(long messageOffset) {
        // Single threaded execution preserves strict ordering.
        lastConsumedOffset.set(messageOffset);
    }

    public boolean hasUncommittedMessages() {
        // Thread safety: it's ok to use a snapshot of the lastConsumedOffset, as we will have constant progress on this value.
        // So it doesn't matter if we use a bit outdated value; we would be exact if we called this method a few milliseconds before. ;-)

        return lastComittedOffset.get() < (lastConsumedOffset.get() + 1);
    }

    public long getCommitOffsetAndClear() {
        // Commit offset always points to next unconsumed message.
        // Thread safety: see hasUncommittedMessages()

        lastComittedOffset.set(lastConsumedOffset.get() + 1);
        return lastComittedOffset.get();
    }


    // Flow control --------------------------------------------------

    public boolean isPaused() {
        // TODO some fancy error rate / circuit breaker stuff
        return numberOfUnprocessedMessages() > MAX_MESSAGES_IN_FLIGHT;
    }

    public boolean shouldResume() {
        // simple logic for now
        return !isPaused();
    }
}
