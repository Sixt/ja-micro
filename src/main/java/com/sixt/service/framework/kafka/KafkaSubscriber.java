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

import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.util.ReflectionUtil;
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

public class KafkaSubscriber<TYPE> implements Runnable, ConsumerRebalanceListener {

    private static final Logger logger = LoggerFactory.getLogger(KafkaSubscriber.class);

    public final static int THROTTLE_MESSAGE_COUNT = 100;

    public enum OffsetReset {
        Earliest, Latest;
    }

    protected EventReceivedCallback<TYPE> callback;
    protected String topic;
    protected String groupId = UUID.randomUUID().toString();
    protected boolean enableAutoCommit = false;
    protected KafkaSubscriber.OffsetReset offsetReset = KafkaSubscriber.OffsetReset.Earliest;
    protected int minThreads;
    protected int maxThreads;
    protected int idleTimeoutSeconds;
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

    KafkaSubscriber(EventReceivedCallback<TYPE> callback, String topic,
                           String groupId, boolean enableAutoCommit, OffsetReset offsetReset,
                           int minThreads, int maxThreads, int idleTimeoutSeconds, int pollTime) {
        this.callback = callback;
        this.topic = topic;
        this.groupId = groupId;
        this.enableAutoCommit = enableAutoCommit;
        this.offsetReset = offsetReset;
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.pollTime = pollTime;

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
            offsetCommitter = new OffsetCommitter(realConsumer, Clock.systemUTC());
            primaryExecutor = Executors.newSingleThreadExecutor();
            primaryExecutor.submit(this);
            isInitialized.set(true);
        } catch (Exception ex) {
            logger.error("Error building Kafka consumer", ex);
        }
    }

    public void consume(KafkaTopicInfo message) {
        TopicPartition tp = message.getTopicPartition();
        synchronized (messagesForConsume) {
            Long previous = messagesForConsume.get(tp);
            if (previous == null || previous.longValue() < message.getOffset()) {
                messagesForConsume.put(tp, message.getOffset());
            }
        }
    }

    @Override
    public void run() {
        while (! shutdownMutex.get()) {
            try {
                readMessages();
                consumeMessages();
                offsetCommitter.recommitOffsets();
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
                        record.offset());
                KafkaSubscriberWorker worker = null;
                if (useProtobuf) {
                    Message proto = ProtobufUtil.jsonToProtobuf(rawMessage,
                            (Class<? extends Message>) messageType);
                    worker = new KafkaSubscriberWorker((TYPE) proto, topicInfo);
                } else {
                    worker = new KafkaSubscriberWorker((TYPE) rawMessage, topicInfo);
                }
                executor.submit(worker);
                messageBacklog.incrementAndGet();
            }
        }
    }

    protected void consumeMessages() {
        synchronized (messagesForConsume) {
            for (TopicPartition tp : messagesForConsume.keySet()) {
                Long offset = messagesForConsume.get(tp);
                Map<TopicPartition, OffsetAndMetadata> offsetMap = Collections.singletonMap(
                        tp, new OffsetAndMetadata(offset + 1));
                realConsumer.commitSync(offsetMap);
                offsetCommitter.offsetCommitted(offsetMap);
            }
            messagesForConsume.clear();
        }
    }

    public void shutdown() {
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
            try {
                callback.eventReceived(message, topicInfo);
            } catch (Exception ex) {
                logger.warn("Caught exception in event callback", ex);
            }
            messageBacklog.decrementAndGet();
        }
    }

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
