package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PriorityMessageQueue implements MessageQueue {

    private Map<Integer, TreeSet<ConsumerRecord<String, String>>> partitionQueue;
    private Map<Integer, ConsumerRecord<String, String>> inProgress;
    private MessageExecuter messageExecuter;
    private ScheduledExecutorService retryExecutor;
    private long retryDelayMillis;

    public PriorityMessageQueue(MessageExecuter messageExecuter, long retryDelayMillis) {
        this.partitionQueue = new HashMap<>();
        this.inProgress = new HashMap<>();
        this.messageExecuter = messageExecuter;
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor();
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public synchronized void add(ConsumerRecord<String, String> record) {
        int partition = record.partition();
        TreeSet<ConsumerRecord<String, String>> pQueue = partitionQueue.get(partition);
        if (pQueue == null) {
            pQueue = new TreeSet<>(Comparator.comparingLong(ConsumerRecord::offset));
            partitionQueue.put(partition, pQueue);
        }
        pQueue.add(record);

        if (!inProgress.containsKey(partition)) {
            processNext(partition);
        }
    }

    @Override
    public synchronized void consumed(KafkaTopicInfo topicInfo) {
        inProgress.remove(topicInfo.getPartition());
        processNext(topicInfo.getPartition());
    }

    @Override
    public synchronized void processingEnded(KafkaTopicInfo topicInfo) {
        final ConsumerRecord<String, String> record = inProgress.get(topicInfo.getPartition());
        if (record != null && record.offset() == topicInfo.getOffset()) {
            //Processing failed. Schedule retry.
            retryExecutor.schedule(() -> messageExecuter.execute(record), retryDelayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void processNext(int partition) {
        TreeSet<ConsumerRecord<String, String>> pQueue = partitionQueue.get(partition);
        if (pQueue != null && !pQueue.isEmpty()) {
            ConsumerRecord<String, String> record = pQueue.first();
            messageExecuter.execute(record);
            inProgress.put(partition, record);
            pQueue.pollFirst();
        }

    }
}
