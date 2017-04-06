package com.sixt.service.framework.kafka.messaging;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.sixt.service.framework.OrangeContext;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
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

    public static final int MAX_MESSAGES_IN_FLIGHT = 100;

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

    // Metrics and tracing
    private Tracer tracer; // FIXME inject me


    // Lifecycle --------------------------------------------------

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

        public MessageDeliveryTask(ConsumerRecord<String, byte[]> record) {
            this.record = record;
        }

        @Override
        public void run() {
            if (isStopped.get()) {
                return; // empty the queue if the processor was stopped.
            }

            Message<? extends com.google.protobuf.Message> message = parseMessage();
            if (message == null) {
                return; // Could not even parse the message, so we give up.
            }

            deliverToMessageHandler(message);
        }

        private Message<? extends com.google.protobuf.Message> parseMessage() {
            try {
                Envelope envelope = Envelope.parseFrom(record.value());

                MessageType type = new MessageType(envelope.getMessageType());
                Parser<com.google.protobuf.Message> parser = typeDictionary.parserFor(type);

                if (parser == null) {
                    throw new UnknownMessageTypeException(type);
                }

                com.google.protobuf.Message innerMessage = parser.parseFrom(envelope.getInnerMessage());
                return Messages.fromKafka(innerMessage, envelope, record);

            } catch (InvalidProtocolBufferException | UnknownMessageTypeException unrecoverableParsingError) {
                // FIXME structured logging
                logger.warn("Cannot parse message", unrecoverableParsingError);

            } catch (Throwable unexpectedError) {
                // FIXME structured logging
                logger.error("FIXME", unexpectedError);
            }

            return null;
        }


        private void deliverToMessageHandler(Message message) {
            boolean deliverMessage = true;

            OrangeContext context = message.getMetadata().newContextFromMetadata();

            /*
            Span span = tracer.buildSpan(message.getMetadata().getType().toString()).start();
            Tags.SPAN_KIND.set(span, "consumer");
            span.setTag("correlation_id", context.getCorrelationId());
            context.setTracingContext(span.context());
*/

            while (deliverMessage) {
                try {
                    MessageHandler handler = typeDictionary.messageHandlerFor(message.getMetadata().getType());
                    if (handler == null) {
                        throw new UnknownMessageHandlerException(message.getMetadata().getType());
                    }

                    // Leave the framework here: hand over execution to service-specific handler.
                    handler.onMessage(message, context);
                    break; // tricky: break here and deliverMessage stays true, this way we can distinguish between failure and success below.

                } catch (Throwable error) {
                    // Should we retry to deliver the failed message?
                    deliverMessage = failedMessageProcessor.onFailedMessage(message, error);
                }
            }
            // finally, consume the message - even if delivery failed
            markAsConsumed(message.getMetadata().getOffset());

            /*
            if (!deliverMessage) {
                Tags.ERROR.set(span, true);
            }
            span.finish();
            */
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
        return numberOfUnprocessedMessages() > MAX_MESSAGES_IN_FLIGHT;
    }

    public boolean shouldResume() {
        // simple logic for now
        return !isPaused();
    }

    // Test access
    TypeDictionary getTypeDictionary() {
        return typeDictionary;
    }
}
