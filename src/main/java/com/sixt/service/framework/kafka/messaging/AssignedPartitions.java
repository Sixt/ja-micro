package com.sixt.service.framework.kafka.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AssignedPartitions represents the set of PartitionProcessors for the partitions assigned to a certain consumer.
 *
 * Read records are dispatched to the right processor. It aggregates over the individual processors to get the set of
 * committed offset, paused or resumed partitions.
 *
 * Additionally it manages creation, termination and removal of PartitionProcessors as partitions are distributed across
 * consumers.
 *
 * Thread safety policy: single-thread use by MessagingConsumer
 */
final class AssignedPartitions {
    private static final Logger logger = LoggerFactory.getLogger(AssignedPartitions.class);

    private final PartitionProcessorFactory processorFactory;
    private final Map<TopicPartition, PartitionProcessor> processors = new HashMap<>();

    AssignedPartitions(PartitionProcessorFactory processorFactory) {
        this.processorFactory = processorFactory;
    }

    Set<TopicPartition> allPartitions() {
        Set<TopicPartition> assignedPartitions = new HashSet<>();
        processors.forEach((key, partition) -> assignedPartitions.add(key));
        return assignedPartitions;
    }

    void assignNewPartitions(Collection<TopicPartition> assignedPartitions) {
        assignedPartitions.forEach((key) -> assignNewPartition(key));
    }

    PartitionProcessor assignNewPartition(TopicPartition partitionKey) {
        PartitionProcessor proccessor = processorFactory.newProcessorFor(partitionKey);
        processors.put(partitionKey, proccessor);

        return proccessor;
    }

    void enqueue(ConsumerRecords<String, byte[]> records) {
        records.forEach((record) -> {
            TopicPartition partitionKey = new TopicPartition(record.topic(), record.partition());

            PartitionProcessor processor = processors.get(partitionKey);
            if (processor == null) {
                processor = assignNewPartition(partitionKey);
            }

            processor.enqueue(record);
        });
    }

    Map<TopicPartition, OffsetAndMetadata> offsetsToBeCommitted() {
        Map<TopicPartition, OffsetAndMetadata> commitOffsets = new HashMap<>();

        processors.forEach((key, processor) -> {
            if (processor.hasUncommittedMessages()) {
                commitOffsets.put(key, new OffsetAndMetadata(processor.getCommitOffsetAndClear()));
            }
        });

        return commitOffsets;
    }

    Collection<TopicPartition> partitionsToBePaused() {
        List<TopicPartition> pausedPartitions = new ArrayList<>();

        processors.forEach((key, processor) -> {
            if (processor.isPaused()) {
                pausedPartitions.add(key);
            }
        });

        return pausedPartitions;
    }

    Collection<TopicPartition> partitionsToBeResumed() {
        List<TopicPartition> resumeablePartitions = new ArrayList<>();

        processors.forEach((key, processor) -> {
            if (processor.shouldResume()) {
                resumeablePartitions.add(key);
            }
        });

        return resumeablePartitions;
    }

    void stopProcessing(Collection<TopicPartition> partitions) {
        partitions.forEach((key) -> {
            PartitionProcessor processor = processors.get(key);

            if (processor == null) {
                logger.warn("Ignored operation: trying to stop a non-existing processor for partition {}", key);
                return;
            }

            processor.stopProcessing();
        });
    }

    void waitForHandlersToComplete(Collection<TopicPartition> partitions, long timeoutMillis) {
        partitions.forEach((key) -> {
            PartitionProcessor processor = processors.get(key);

            if (processor == null) {
                logger.warn("Ignored operation: trying to waitForHandlersToTerminate on a non-existing processor for partition {}", key);
                return;
            }

            processor.waitForHandlersToTerminate(timeoutMillis);
        });
    }

    void removePartitions(Collection<TopicPartition> revokedPartitions) {
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
