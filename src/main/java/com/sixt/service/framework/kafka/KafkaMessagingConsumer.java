package com.sixt.service.framework.kafka;

import com.google.common.collect.Lists;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.logstash.logback.marker.Markers.append;

// TODO rename to reflect the use case "asynchronous messaging" vs. "event subscriber"
public class KafkaMessagingConsumer<TYPE> implements Runnable, ConsumerRebalanceListener {

    private static final Logger logger = LoggerFactory.getLogger(KafkaSubscriber.class);

    public final static int THROTTLE_MESSAGE_COUNT = 100;

    public enum OffsetReset {
        Earliest, Latest;
    }

    protected String topic;
    protected String groupId = UUID.randomUUID().toString();

    // TODO don't we consume only not yet commited messages?
    protected KafkaMessagingConsumer.OffsetReset offsetReset = KafkaMessagingConsumer.OffsetReset.Latest;

    protected boolean enableAutoCommit = false;



    protected BlockingQueue<Runnable> messagesToBeProcessedQueue = new LinkedBlockingQueue<>();


    protected ExecutorService pollLoopExecutor;
    protected ThreadPoolExecutor messageHandlerExecutor;



    protected Map<TopicPartition, Long> lastConsumedOffset;
    protected KafkaConsumer<String, byte[]> realConsumer;
    protected Class<? extends Object> messageType;
    protected final int pollTime;
    protected boolean useProtobuf = true;


    protected AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    protected OffsetCommitter offsetCommitter;
    protected AtomicInteger messageBacklog = new AtomicInteger(0);
    protected AtomicBoolean isReadingPaused = new AtomicBoolean(false);
    protected AtomicBoolean isInitialized = new AtomicBoolean(false);

    KafkaMessagingConsumer(String topic,
                           String consumerGroupId, boolean enableAutoCommit, KafkaMessagingConsumer.OffsetReset offsetReset,
                           int pollTime) {
        this.topic = topic;
        this.groupId = consumerGroupId;
        this.enableAutoCommit = enableAutoCommit;
        this.offsetReset = offsetReset;
        this.pollTime = pollTime;


        lastConsumedOffset = new HashMap<>();


        /*
        Threading model:
        1. Each consumer runs a single-threaded poll-Loop, serving potentially multiple partitions
        If we need higher concurrency, we assign additional consumers (up to the number of partitions) by either spinning
        additonal service instances or having multiple consumsers in one instance.

        2. Message processing is delegated to separate worker threads.
        For now, we go single-threaded here.
                If we need higher concurrency, we have two options:
        a) assign more consumers (see above)
        b) have multiple message processor threads
        In the case b), we still may want to limit it to at most one thread per partition or we loose ordering semantics.
        */

        // TODO use thread factory to properly name the threads
        messageHandlerExecutor = new ThreadPoolExecutor(1, 1, 24, TimeUnit.HOURS, messagesToBeProcessedQueue);
        pollLoopExecutor = Executors.newSingleThreadExecutor();

    }

    synchronized void initialize(String servers) {
        if (isInitialized.get()) {
            logger.warn("Already initialized");
            return;
        }

        try {
            Properties props = new Properties();
            props.put("bootstrap.servers", servers);
            props.put("group.id", groupId);
            props.put("key.deserializer", StringDeserializer.class.getName());
            props.put("value.deserializer", StringDeserializer.class.getName());
            props.put("heartbeat.interval.ms", "10000");
            props.put("session.timeout.ms", "20000");
            props.put("enable.auto.commit", Boolean.toString(enableAutoCommit));
            props.put("auto.offset.reset", offsetReset.toString().toLowerCase());
            realConsumer = new KafkaConsumer<>(props);
            realConsumer.subscribe(Lists.newArrayList(topic), this);

            // FIXME
            offsetCommitter = new OffsetCommitter(realConsumer, Clock.systemUTC());

            pollLoopExecutor.submit(this);
            isInitialized.set(true);
        } catch (Exception ex) {
            logger.error("Error building Kafka consumer", ex);
        }
    }

    public void consume(KafkaTopicInfo message) {
        TopicPartition tp = message.getTopicPartition();
        synchronized (lastConsumedOffset) {
            Long previous = lastConsumedOffset.get(tp);
            if (previous == null || previous.longValue() < message.getOffset()) {
                lastConsumedOffset.put(tp, message.getOffset());
            }
        }
    }

    @Override
    public void run() {
        while (!isShuttingDown.get()) {
            try {
                readMessages();
                consumeMessages();
                offsetCommitter.recommitOffsets();
            } catch (CommitFailedException ex) {
                logger.warn("Caught CommitFailedException in message loop", ex);
            } catch (Exception ex) {
                logger.error("Caught exception in message loop", ex);
            }
        }
        realConsumer.close();
    }

    @SuppressWarnings(value = "unchecked")
    protected void readMessages() {
        logger.trace("Reading messages from Kafka...");

        checkForMessageThrottling();

        ConsumerRecords<String, String> records = realConsumer.poll(pollTime);
        if (records != null) {
            for (ConsumerRecord<String, String> record : records) {
                String rawMessage = record.value();

                logger.debug(append("rawMessage", rawMessage), "Read Kafka message ({})", record.offset());
                KafkaTopicInfo topicInfo = new KafkaTopicInfo(topic, record.partition(),
                        record.offset(), record.key());

                MessageDeliveryWorker worker = null;
                if (useProtobuf) {
                    com.google.protobuf.Message proto = ProtobufUtil.jsonToProtobuf(rawMessage,
                            (Class<? extends com.google.protobuf.Message>) messageType);
                    worker = new MessageDeliveryWorker((TYPE) proto, topicInfo);
                } else {
                    worker = new MessageDeliveryWorker((TYPE) rawMessage, topicInfo);
                }
                messageHandlerExecutor.submit(worker);
                messageBacklog.incrementAndGet();
            }
        }
    }

    protected void consumeMessages() {
        synchronized (lastConsumedOffset) {
            for (TopicPartition tp : lastConsumedOffset.keySet()) {
                Long offset = lastConsumedOffset.get(tp);
                Map<TopicPartition, OffsetAndMetadata> offsetMap = Collections.singletonMap(
                        tp, new OffsetAndMetadata(offset + 1));
                realConsumer.commitSync(offsetMap);
                offsetCommitter.offsetCommitted(offsetMap);
            }
            lastConsumedOffset.clear();
        }
    }

    public void shutdown() {
        pollLoopExecutor.shutdown();
        isShuttingDown.set(true);
        try {
            pollLoopExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    protected class MessageDeliveryWorker implements Runnable {

        protected Message message;
        protected MessageHandler handler;

        public MessageDeliveryWorker(Message message, MessageHandler handler) {
            this.message = message;
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                // TODO fill in correlation id etc.
                OrangeContext context = new OrangeContext();
                handler.onMessage(message, context);

            } catch (Throwable ex) {
                logger.warn("Caught exception in event callback", ex);

                // any exception here is something serious (e.g. infrastructure failure) or the result of a lazy developer
                // TODO we need something like a circuit breaker that pauses message delivery if we have high failure rate
                // TODO for occational failures, we push the message to a failed message topic
                // TODO and have some other piece (a second MessagingConsumer?) take care of it
            } finally {
                // commit message in all cases to get the topic on going
                // TODO create a means to consume the message already in the handler
                consume(message.getMetadata().getTopicInfo());
                messageBacklog.decrementAndGet();
            }
        }
    }


    // Partitioning handling

    @Override
    public synchronized void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        offsetCommitter.partitionsRevoked(partitions);
    }

    @Override
    public synchronized void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        offsetCommitter.partitionsAssigned(partitions);
    }


    protected void checkForMessageThrottling() {
        if (messageBacklog.get() >= THROTTLE_MESSAGE_COUNT) {
            pauseAssignedPartitions();
        } else {
            resumeAssignedPartitions();
        }
    }

    protected synchronized void pauseAssignedPartitions() {
        if (!isReadingPaused.get()) {
            logger.info("Pausing kafka reading");
            realConsumer.pause(realConsumer.assignment());

            isReadingPaused.set(true);
        }
    }

    protected synchronized void resumeAssignedPartitions() {
        if (isReadingPaused.get()) {
            logger.info("Resuming kafka reading");
            realConsumer.resume(realConsumer.assignment());

            isReadingPaused.set(false);
        }
    }

}
