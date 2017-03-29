package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.kafka.KafkaSubscriber;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class AssignedPartitions {
    private static final Logger logger = LoggerFactory.getLogger(AssignedPartitions.class);

    
    private final Map<TopicPartition, PartitionProcessor> processors = new HashMap<>();

    public void assignNewPartitions(Collection<TopicPartition> assignedPartitions) {
        assignedPartitions.forEach((key) -> assignNewPartition(key));
    }

    public PartitionProcessor assignNewPartition(TopicPartition partitionKey) {
        // FIXME

        return null;
    }

    public void enqueue(ConsumerRecords<String, byte[]> records) {
        records.forEach((record) -> {
            TopicPartition partitionKey = new TopicPartition(record.topic(), record.partition());

            PartitionProcessor processor = processors.get(partitionKey);
            if (processor == null) {
                processor = assignNewPartition(partitionKey);
            }

            processor.enqueue(record);
        });
    }

    public Collection<TopicPartition> partitionsToBePaused() {
        List<TopicPartition> pausedPartitions = new ArrayList<>();

        processors.forEach((key, processor) -> {
          if(processor.isPaused()) {
              pausedPartitions.add(key);
          }
        });

        return pausedPartitions;
    }

    public Collection<TopicPartition> partitionsToBeResumed() {
        List<TopicPartition> resumeablePartitions = new ArrayList<>();

        processors.forEach((key, processor) -> {
            if(processor.shouldResume()) {
                resumeablePartitions.add(key);
            }
        });

        return resumeablePartitions;
    }

    public Map<TopicPartition, OffsetAndMetadata> offsetsToBeCommitted() {
        Map<TopicPartition, OffsetAndMetadata> commitOffsets = new HashMap<>();

        processors.forEach((key, processor) -> {
            if (processor.hasUncommittedMessages()) {
                commitOffsets.put(key, new OffsetAndMetadata(processor.getCommitOffsetAndClear()));
            }
        });

        return commitOffsets;
    }

    public void stopProcessing(Collection<TopicPartition> partitions) {
        partitions.forEach((key) -> {
            PartitionProcessor processor = processors.get(key);

            if (processor == null) {
                logger.warn("Ignored operation: trying to stop a non-existing processor for partition {}", key);
                return;
            }

            processor.stop();
        });
    }

    public void waitForHandlersToComplete(Collection<TopicPartition> partitions, long timeoutMillis) {
        partitions.forEach((key) -> {
            PartitionProcessor processor = processors.get(key);

            if (processor == null) {
                logger.warn("Ignored operation: trying to waitForHandlerToComplete on a non-existing processor for partition {}", key);
                return;
            }

            processor.waitForHandlerToComplete(timeoutMillis);
        });
    }

    public void removePartitions(Collection<TopicPartition> revokedPartitions) {
        // Must have stopped the partition processing before
        revokedPartitions.forEach((key) -> {
            PartitionProcessor processor = processors.get(key);

            if (processor == null) {
                return; // idempotent
            }

            if (!processor.isTerminated()) {
                throw new IllegalStateException("Processor must be terminated before removing it.");
            }

            processors.remove(key);
        });
    }

}
