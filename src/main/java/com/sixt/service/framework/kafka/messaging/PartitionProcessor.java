package com.sixt.service.framework.kafka.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.metrics.GoCounter;
import com.sixt.service.framework.metrics.GoTimer;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import net.logstash.logback.marker.LogstashMarker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import javax.validation.constraints.Null;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static net.logstash.logback.marker.Markers.append;


final class PartitionProcessor {
    private static final Logger logger = LoggerFactory.getLogger(PartitionProcessor.class);

    static final int MAX_MESSAGES_IN_FLIGHT = 100;

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
    private final AtomicLong lastConsumedOffset = new AtomicLong(-2); // i.e. unknown
    private final AtomicLong lastComittedOffset = new AtomicLong(-1); // i.e. unknown

    // Tracing and metrics (optional)
    @Null private final Tracer tracer;
    @Null private final MetricBuilderFactory metricsBuilderFactory;

    // Lifecycle --------------------------------------------------

    PartitionProcessor(TopicPartition partitionKey, TypeDictionary typeDictionary, FailedMessageProcessor failedMessageProcessor, Tracer tracer, MetricBuilderFactory metricBuilderFactory) {
        this.partitionKey = partitionKey;
        this.typeDictionary = typeDictionary;
        this.failedMessageProcessor = failedMessageProcessor;

        this.tracer = tracer;
        this.metricsBuilderFactory = metricBuilderFactory;

        undeliveredMessages = new LinkedBlockingQueue<>();

        // Single threaded execution per partition to preserve ordering guarantees.
        // EXTENSION:
        // - if required, allow multiple threads sacrificing ordering.
        // - but then the commmit offset handling requires more thoughts
        executor = new ThreadPoolExecutor(1, 1, 24, TimeUnit.HOURS, undeliveredMessages);
    }

    void stopProcessing() {
        // We mark this dispatcher as stopped, so no new tasks will execute.
        isStopped.set(true);
        executor.shutdown();
    }

    boolean isTerminated() {
        return isTerminated.get();
    }

    void waitForHandlersToTerminate(long timeoutMillis) {
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

    void enqueue(ConsumerRecord<String, byte[]> record) {
        if (isStopped.get()) {
            logger.info("Ignored records to be enqueued after PartitionProcessor {} was stopped.", partitionKey);
            return;
        }

        executor.submit(new MessageDeliveryTask(record));
    }


    class MessageDeliveryTask implements Runnable {

        private final ConsumerRecord<String, byte[]> record;

        // Tracing / metrics stuff (optional, may be null)
        private
        @Null
        Span span;
        private
        @Null
        GoTimer handlerTimer;
        private long startTime;


        MessageDeliveryTask(ConsumerRecord<String, byte[]> record) {
            this.record = record;
        }

        @Override
        public void run() {
            if (isStopped.get()) {
                return; // empty the queue if the processor was stopped.
            }

            try {
                Message<? extends com.google.protobuf.Message> message = parseMessage();
                if (message == null) {
                    return; // Could not even parse the message, so we give up.
                }

                deliverToMessageHandler(message);

            } catch (Throwable unexpectedError) {
                // Anything that reaches here could be potentially a condition that the thread could not recover from.
                // see https://docs.oracle.com/javase/specs/jls/se8/html/jls-11.html#jls-11.1
                //
                // Thus, we try to log the error, but let the thread die.
                // The thread pool will create a new thread is the hosting process itself is still alive.

                logger.error("Unexpected error while handling message", unexpectedError);
                throw unexpectedError;
            }
        }


        private Message<? extends com.google.protobuf.Message> parseMessage() {
            Envelope envelope = null;

            try {

                envelope = Envelope.parseFrom(record.value());

            } catch (InvalidProtocolBufferException parseError) {
                parsingFailed(envelope);
                logger.warn(logMarkerFromRecordAndEnvelope(envelope), "Cannot parse Envelope from raw record", parseError);
                return null;
            }

            try {
                MessageType type = new MessageType(envelope.getMessageType());
                Parser<com.google.protobuf.Message> parser = typeDictionary.parserFor(type);

                if (parser == null) {
                    throw new UnknownMessageTypeException(type);
                }

                com.google.protobuf.Message innerMessage = parser.parseFrom(envelope.getInnerMessage());
                return Messages.fromKafka(innerMessage, envelope, record);

            } catch (InvalidProtocolBufferException | UnknownMessageTypeException unrecoverableParsingError) {
                parsingFailed(envelope);
                logger.warn(logMarkerFromRecordAndEnvelope(envelope), "Cannot parse inner payload message", unrecoverableParsingError);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private void deliverToMessageHandler(Message message) {
            boolean tryDeliverMessage = true;
            boolean deliveryFailed = true;

            OrangeContext context = message.getMetadata().newContextFromMetadata();

            try {
                while (tryDeliverMessage) {
                    try {
                        MessageType messageType = message.getMetadata().getType();
                        MessageHandler handler = typeDictionary.messageHandlerFor(messageType);
                        if (handler == null) {
                            throw new UnknownMessageHandlerException(messageType);
                        }

                        deliveryStarted(message, handler, context);

                        // Leave the framework here: hand over execution to service-specific handler.
                        handler.onMessage(message, context);
                        deliveryFailed = false;

                        break;

                    } catch (Exception failure) {
                        // Strategy decides: Should we retry to deliver the failed message?
                        tryDeliverMessage = failedMessageProcessor.onFailedMessage(message, failure);
                        deliveryFailed(message, failure, tryDeliverMessage);
                    }
                }

            } finally {
                // consume the message - even if delivery failed
                markAsConsumed(message.getMetadata().getOffset());
                deliveryEnded(message, deliveryFailed);
            }


        }


        // Helper methods to get the glue code for debug logging, tracing and metrics out of the main control flow

        private void parsingFailed(Envelope envelope) {
            String messageType = "Envelope";
            if (envelope != null) {
                messageType = envelope.getMessageType();
            }

            if (metricsBuilderFactory != null) {
                // TODO all metrics - should we add the topic?
                GoCounter parsingFailureCounter = metricsBuilderFactory.newMetric("messaging_consumer_parse_failures")
                        .withTag("messageType", messageType).buildCounter();

                parsingFailureCounter.incFailure();
            }
        }


        private void deliveryStarted(Message message, MessageHandler handler, OrangeContext context) {
            logger.debug(message.getMetadata().getLoggingMarker(), "Calling {}.onMessage({})", handler.getClass().getTypeName(), message.getMetadata().getType());

            if (tracer != null) {
                span = tracer.buildSpan(message.getMetadata().getType().toString()).start();
                Tags.SPAN_KIND.set(span, "consumer");
                span.setTag("correlation_id", context.getCorrelationId());
                context.setTracingContext(span.context());
            }

            if (metricsBuilderFactory != null) {
                handlerTimer = metricsBuilderFactory.newMetric("messaging_consumer_message_handler")
                        .withTag("messageType", message.getMetadata().getType().toString())
                        .buildTimer();
                startTime = handlerTimer.start();
            }
        }

        private void deliveryFailed(Message message, Exception failure, boolean tryDeliverMessage) {
            logger.debug(message.getMetadata().getLoggingMarker(), "Received tryDeliverMessage={} from {}.onFailedMessage({})", tryDeliverMessage, failedMessageProcessor.getClass().getTypeName(), failure.toString());

            if (metricsBuilderFactory != null) {
                metricsBuilderFactory.newMetric("messaging_consumer_delivery_failures")
                        .withTag("retryable", Boolean.toString(tryDeliverMessage))
                        .withTag("messageType", message.getMetadata().getType().toString())
                        .buildCounter();
            }

        }

        private void deliveryEnded(Message message, boolean deliveryFailed) {
            logger.debug(message.getMetadata().getLoggingMarker(), "Message {} with offset {} marked as consumed.", message.getMetadata().getType(), message.getMetadata().getOffset());

            if (span != null) {
                if (deliveryFailed) {
                    Tags.ERROR.set(span, true);
                }
                span.finish();
            }

            if (metricsBuilderFactory != null) {
                GoCounter consumedMessages = metricsBuilderFactory.newMetric("messaging_consumer_consumed_messages")
                        .withTag("messageType", message.getMetadata().getType().toString())
                        .buildCounter();

                if (deliveryFailed) {
                    handlerTimer.recordFailure(startTime);
                    consumedMessages.incFailure();
                } else {
                    handlerTimer.recordSuccess(startTime);
                    consumedMessages.incSuccess();
                }
            }

        }


        private Marker logMarkerFromRecordAndEnvelope(Envelope envelope) {
            LogstashMarker logMarker = append("topic", record.topic())
                    .and(append("partitionId", record.partition()))
                    .and(append("distributionKey", record.key()))
                    .and(append("offset", record.offset()));

            if (envelope != null) {
                logMarker
                        .and(append("messageId", envelope.getMessageId()))
                        .and(append("correlationId", envelope.getCorrelationId()))
                        .and(append("messageType", envelope.getMessageType()));
            }

            return logMarker;
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

    boolean hasUncommittedMessages() {
        // Thread safety: it's ok to use a snapshot of the lastConsumedOffset, as we will have constant progress on this value.
        // So it doesn't matter if we use a bit outdated value; we would be exact if we called this method a few milliseconds before. ;-)

        return lastComittedOffset.get() < (lastConsumedOffset.get() + 1);
    }

    long getCommitOffsetAndClear() {
        // Commit offset always points to next unconsumed message.
        // Thread safety: see hasUncommittedMessages()

        lastComittedOffset.set(lastConsumedOffset.get() + 1);
        return lastComittedOffset.get();
    }


    // Flow control --------------------------------------------------

    boolean isPaused() {
        return numberOfUnprocessedMessages() > MAX_MESSAGES_IN_FLIGHT;
    }

    boolean shouldResume() {
        // simple logic for now
        return !isPaused();
    }

    // Test access
    TypeDictionary getTypeDictionary() {
        return typeDictionary;
    }
}
