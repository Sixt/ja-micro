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

package com.sixt.service.framework.kafka;

import com.google.protobuf.Message;
import com.sixt.service.framework.metrics.GoCounter;
import com.sixt.service.framework.metrics.GoGauge;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.util.ReflectionUtil;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.sixt.service.framework.OrangeContext.CORRELATION_ID;
import static net.logstash.logback.marker.Markers.append;

public class KafkaSubscriber<TYPE> implements Runnable, ConsumerRebalanceListener, MessageExecuter {

    private static final Logger logger = LoggerFactory.getLogger(KafkaSubscriber.class);

    public enum OffsetReset {
        Earliest, Latest;
    }

    public enum QueueType {
        Eager(EagerMessageQueue.class),
        Priority(PriorityMessageQueue.class);

        private Class<? extends MessageQueue> messageQueueType;

        QueueType(Class<? extends MessageQueue> messageQueueType) {
            this.messageQueueType = messageQueueType;
        }

        public MessageQueue getMessageQueueInstance(MessageExecuter executor, long retryDelayMillis) {
            try {
                Constructor<? extends MessageQueue> c = messageQueueType.getConstructor(MessageExecuter.class, long.class);
                return c.newInstance(executor, retryDelayMillis);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to create message queue instance of type " + name(), e);
            }
        }
    }

    protected EventReceivedCallback<TYPE> callback;
    protected String topic;
    protected String groupId;
    protected boolean enableAutoCommit;
    protected KafkaSubscriber.OffsetReset offsetReset;
    protected int minThreads;
    protected int maxThreads;
    protected int idleTimeoutSeconds;
    protected int throttleLimit;
    protected BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
    protected ThreadPoolExecutor executor;
    protected Map<TopicPartition, Long> messagesForConsume;
    protected KafkaConsumer<String, String> realConsumer;
    protected Class<? extends Object> messageType;
    protected final int pollTime;
    protected boolean useProtobuf = true;
    protected ExecutorService primaryExecutor;
    protected AtomicBoolean shutdownMutex = new AtomicBoolean(false);
    protected OffsetCommitter offsetCommitter;
    protected AtomicInteger messageBacklog = new AtomicInteger(0);
    protected AtomicBoolean isReadingPaused = new AtomicBoolean(false);
    protected AtomicBoolean isInitialized = new AtomicBoolean(false);
    private GoCounter messagesReadMetric = new GoCounter("");
    private GoGauge messageBacklogMetric = new GoGauge("");
    private GoCounter rebalaceMetric = new GoCounter("");
    private MetricBuilderFactory metricBuilderFactory;
    private PartitionAssignmentWatchdog partitionAssignmentWatchdog;
    private MessageQueue messageQueue;

    KafkaSubscriber(EventReceivedCallback<TYPE> callback, String topic,
                    String groupId, boolean enableAutoCommit, OffsetReset offsetReset,
                    int minThreads, int maxThreads, int idleTimeoutSeconds, int pollTime,
                    int throttleLimit, QueueType queueType, long retryDelayMillis) {
        this.callback = callback;
        this.topic = topic;
        this.groupId = groupId;
        this.enableAutoCommit = enableAutoCommit;
        this.offsetReset = offsetReset;
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.pollTime = pollTime;
        this.throttleLimit = throttleLimit;

        try {
            //the EventReceivedCallback class needs to use the generic parameter
            //so that we can determine if we should call using String or protobuf
            messageType = ReflectionUtil.findSubClassParameterType(callback, 0);
            if (messageType == null) {
                throw new IllegalStateException("Could not determine EventReceivedCallback message type");
            }
            if (String.class.equals(messageType)) {
                useProtobuf = false;
            }
        } catch (ClassNotFoundException e) {
            logger.error("Could not determine EventReceivedCallback message type");
        }
        messagesForConsume = new HashMap<>();
        executor = new ThreadPoolExecutor(this.minThreads, this.maxThreads, this.idleTimeoutSeconds,
                TimeUnit.SECONDS, workQueue);
        messageQueue = queueType.getMessageQueueInstance(this, retryDelayMillis);
    }

    public void setMetricBuilderFactory(MetricBuilderFactory metricBuilderFactory) {
        this.metricBuilderFactory = metricBuilderFactory;
    }

    public void setPartitionAssignmentWatchdog(PartitionAssignmentWatchdog partitionAssignmentWatchdog) {
        this.partitionAssignmentWatchdog = partitionAssignmentWatchdog;
    }

    synchronized void initialize(String servers, String username, String password) {
        if (isInitialized.get()) {
            logger.warn("Already initialized");
            return;
        }

        buildMetrics();
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
            SaslConfigurator configurator = new SaslConfigurator();
            configurator.configureSasl(props, username, password);
            realConsumer = new KafkaConsumer<>(props);

            List<String> topics = new ArrayList<>();
            topics.add(topic);
            realConsumer.subscribe(topics, this);

            offsetCommitter = new OffsetCommitter(realConsumer, Clock.systemUTC());
            primaryExecutor = Executors.newSingleThreadExecutor();
            primaryExecutor.submit(this);

            if (partitionAssignmentWatchdog != null) {
                partitionAssignmentWatchdog.subscriberInitialized(realConsumer);
            }

            isInitialized.set(true);
        } catch (Exception ex) {
            logger.error("Error building Kafka consumer", ex);
        }
    }

    private void buildMetrics() {
        if (metricBuilderFactory == null) {
            logger.warn("metricBuilderFactory was null");
            return;
        }
        messagesReadMetric = metricBuilderFactory.newMetric("kafka_reads")
                .withTag("topic", topic).withTag("group_id", groupId).buildCounter();
        messageBacklogMetric = metricBuilderFactory.newMetric("kafka_read_backlog")
                .withTag("topic", topic).withTag("group_id", groupId).buildGauge();
        messageBacklogMetric.register("count", messageBacklog::get);
        rebalaceMetric = metricBuilderFactory.newMetric("kafka_rebalance")
                .withTag("topic", topic).withTag("group_id", groupId).buildCounter();
    }

    public void consume(KafkaTopicInfo topicInfo) {
        TopicPartition tp = topicInfo.getTopicPartition();
        synchronized (messagesForConsume) {
            Long previous = messagesForConsume.get(tp);
            if (previous == null || previous.longValue() < topicInfo.getOffset()) {
                messagesForConsume.put(tp, topicInfo.getOffset());
            }
        }
        messageQueue.consumed(topicInfo);
    }

    @Override
    public void run() {
        while (! shutdownMutex.get()) {
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
            messagesReadMetric.incSuccess(records.count());
            for (ConsumerRecord<String, String> record : records) {
                String rawMessage = record.value();
                logger.trace(append("rawMessage", rawMessage), "Read Kafka message ({}/{})",
                        record.partition(), record.offset());

                messageQueue.add(record);
                messageBacklog.incrementAndGet();
            }
        }
    }

    @Override
    public void execute(ConsumerRecord<String, String> record) {
        synchronized (executor) {
        String rawMessage = record.value();
        KafkaTopicInfo topicInfo = new KafkaTopicInfo(topic, record.partition(),
                record.offset(), record.key());
        KafkaSubscriberWorker worker;
        if (useProtobuf) {
            Message proto = ProtobufUtil.jsonToProtobuf(rawMessage,
                    (Class<? extends Message>) messageType);
            worker = new KafkaSubscriberWorker((TYPE) proto, topicInfo);
        } else {
            worker = new KafkaSubscriberWorker((TYPE) rawMessage, topicInfo);
        }
        executor.submit(worker);
        }
    }

    protected void consumeMessages() {
        synchronized (messagesForConsume) {
            for (TopicPartition tp : messagesForConsume.keySet()) {
                Long offset = messagesForConsume.get(tp);
                Map<TopicPartition, OffsetAndMetadata> offsetMap = Collections.singletonMap(
                        tp, new OffsetAndMetadata(offset + 1));
                try {
                    realConsumer.commitSync(offsetMap);
                    offsetCommitter.offsetCommitted(offsetMap);
                } catch (CommitFailedException covfefe) {
                    logger.info("Caught CommitFailedException attempting to commit {} {}", tp, offset);
                }
            }
            messagesForConsume.clear();
        }
    }

    public void shutdown() {
        partitionAssignmentWatchdog.subscriberShutdown(realConsumer);
        primaryExecutor.shutdown();
        shutdownMutex.set(true);
        try {
            primaryExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    protected class KafkaSubscriberWorker implements Runnable {

        protected TYPE message;
        protected KafkaTopicInfo topicInfo;

        public KafkaSubscriberWorker(TYPE message, KafkaTopicInfo topicInfo) {
            this.message = message;
            this.topicInfo = topicInfo;
        }

        @Override
        public void run() {
            MDC.put(CORRELATION_ID, UUID.randomUUID().toString());
            try {
                callback.eventReceived(message, topicInfo);
            } catch (Exception ex) {
                logger.warn("Caught exception in event callback", ex);
            } finally {
                messageQueue.processingEnded(topicInfo);
                messageBacklog.decrementAndGet();
            }
        }
    }

    @Override
    public synchronized void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        offsetCommitter.partitionsRevoked(partitions);
    }

    @Override
    public synchronized void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        rebalaceMetric.incSuccess();
        offsetCommitter.partitionsAssigned(partitions);
    }

    protected void checkForMessageThrottling() {
        if (throttleLimit > 0) {
            if (messageBacklog.get() >= throttleLimit) {
                pauseAssignedPartitions();
            } else {
                resumeAssignedPartitions();
            }
        }
    }

    protected synchronized void pauseAssignedPartitions() {
        if (! isReadingPaused.get()) {
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
