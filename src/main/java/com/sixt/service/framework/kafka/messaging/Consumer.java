package com.sixt.service.framework.kafka.messaging;

import com.google.common.collect.Lists;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer instances are Kafka clients that fetch records of (one or multiple partitions of) a topic.
 * <p>
 * Consumers also handle the flow control (pausing/resuming busy partitions) as well as changes in the partition assignment.
 * <p>
 * The topic a consumer subscribes to must have been created before. When starting a consumer, it will check Kafka if the topic
 * exists.
 * <p>
 * Threading model:
 * The Consumer has a single thread polling Kafka and handing over raw records to PartitionProcessors for further processing.
 * There is one PartitionProcessor per partition.
 * A PartitionProcessor currently is single-threaded to keep the ordering guarantee on a partition.
 * <p>
 * Consumer instances are created by the ConsumerFactory.
 */
public class Consumer {
    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);

    private static final int HANDLER_TIMEOUT_MILLIS = 60_000;
    private static final int POLL_INTERVAL_MILLIS = 300;
    private static final long COMMIT_REFRESH_INTERVAL_MILLIS = 6*60*60*1000; // every six hours

    private final Topic topic;
    private final String consumerGroupId;
    private final KafkaConsumer<String, byte[]> kafka;
    private final AssignedPartitions partitions;
    private final ExecutorService consumerLoopExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isStopped = new AtomicBoolean(false);



    // Build by ConsumerFactory
    Consumer(Topic topic, String consumerGroupId, Properties props, PartitionProcessorFactory processorFactory) {
        this.topic = topic;
        this.consumerGroupId = consumerGroupId;

        // Mandatory settings, not changeable
        props.put("group.id", consumerGroupId);
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", ByteArrayDeserializer.class.getName());

        kafka = new KafkaConsumer<>(props);
        partitions = new AssignedPartitions(processorFactory);

        long now = System.currentTimeMillis();

        // start it
        consumerLoopExecutor.execute(new ConsumerLoop());
    }


    public void shutdown() {
        logger.debug("Shutdown requested for consumer in group {} for topic {}", consumerGroupId, topic.toString());

        isStopped.set(true);
        consumerLoopExecutor.shutdown();


        try {
            consumerLoopExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Set<TopicPartition> allPartitions = partitions.allPartitions();
        partitions.stopProcessing(allPartitions);
        partitions.waitForHandlersToComplete(allPartitions, HANDLER_TIMEOUT_MILLIS);
        kafka.commitSync(partitions.offsetsToBeCommitted());

        kafka.close();

        logger.info("Consumer in group {} for topic {} was shut down.", consumerGroupId, topic.toString());
    }


    class ConsumerLoop implements Runnable {

        // single thread access only
        private long nextCommitRefreshRequiredTimestamp = System.currentTimeMillis() + COMMIT_REFRESH_INTERVAL_MILLIS;

        @Override
        public void run() {
            try {
                kafka.subscribe(Lists.newArrayList(topic.toString()), new PartitionAssignmentChange());
                logger.info("Consumer in group {} subscribed to topic {}", consumerGroupId, topic.toString());
            } catch (Exception unexpected) {
                logger.error("Dead consumer in group {}: Cannot subscribe to topic {}", consumerGroupId, topic.toString(), unexpected);
                return;
            }


            try {
                while (!isStopped.get()) {
                    try {
                        // Note that poll() may also execute the ConsumerRebalanceListener callbacks and may take substantially more time to return.
                        ConsumerRecords<String, byte[]> records = kafka.poll(POLL_INTERVAL_MILLIS);

                        partitions.enqueue(records);
                        kafka.commitSync(partitions.offsetsToBeCommitted());

                        checkIfRefreshCommitRequired();

                        kafka.pause(partitions.partitionsToBePaused());
                        kafka.resume(partitions.partitionsToBeResumed());


                    } catch (Exception kafkaException) {
                        // Example for an exception seen in testing:
                        // CommitFailedException: Commit cannot be completed since the group has already rebalanced and assigned the partitions to another member.

                        // Error handling strategy: log the exception and carry on.
                        // Do not kill the consumer as nobody is there to resurrect a new one.
                        logger.warn("Received exception in ConsumerLoop of Consumer (group=" + consumerGroupId + " ,topic=" + topic.toString() + "). Consumer continues.", kafkaException);
                    }
                }
            } catch (Throwable unexpectedError) {
                logger.error("Unexpected exception in ConsumerLoop of Consumer (group=" + consumerGroupId + " ,topic=" + topic.toString() + "). Consumer now dead.", unexpectedError);

                // Try to close the connection to Kafka
                kafka.close();

                // Since we catched Exception already in the loop, this points to a serious issue where we probably cannot recover from.
                // Thus, we let the thread and the consumer die.
                // Kafka needs to rebalance the group. But we loose a consumer in this instance as nobody takes care to resurrect failed consumers.
                throw unexpectedError;
            }
        }


        private void checkIfRefreshCommitRequired() {
            // Here's the issue:
            // The retention of __consumer_offsets is less than most topics itself, so we need to re-commit regularly to keep the
            // last committed offset per consumer group. This is especially an issue in cases were we have bursty / little traffic.

            Map<TopicPartition, OffsetAndMetadata> commitOffsets = new HashMap<>();
            long now = System.currentTimeMillis();

            if(nextCommitRefreshRequiredTimestamp < now ) {
                nextCommitRefreshRequiredTimestamp = now + COMMIT_REFRESH_INTERVAL_MILLIS;

               for(PartitionProcessor processor : partitions.allProcessors()) {
                    TopicPartition assignedPartition = processor.getAssignedPartition();
                    long lastCommittedOffset = processor.getLastCommittedOffset();

                    // We haven't committed from this partiton yet
                    if(lastCommittedOffset < 0) {
                        OffsetAndMetadata offset = kafka.committed(assignedPartition);
                        if(offset == null) {
                            // there was no commit on this partition at all
                            continue;
                        }
                        lastCommittedOffset = offset.offset();
                        processor.forceSetLastCommittedOffset(lastCommittedOffset);
                    }


                    commitOffsets.put(assignedPartition, new OffsetAndMetadata(lastCommittedOffset));
                }


                kafka.commitSync(commitOffsets);

                logger.info("Refreshing last committed offset {}", commitOffsets);
            }


        }

    }



    class PartitionAssignmentChange implements ConsumerRebalanceListener {
        // From the documentation:
        // 1. This callback will execute in the user thread as part of the {@link Consumer#poll(long) poll(long)} call whenever partition assignment changes.
        // 2. It is guaranteed that all consumer processes will invoke {@link #onPartitionsRevoked(Collection) onPartitionsRevoked} prior to
        //    any process invoking {@link #onPartitionsAssigned(Collection) onPartitionsAssigned}.

        // Observations from the tests:
        // 1. When Kafka rebalances partitions, all currently assigned partitions are revoked and then the remaining partitions are newly assigned.
        // 2. There seems to be a race condition when Kafka is starting up in parallel (e.g. service integration tests): an empty partition set is assigned and
        //    we do not receive any messages.

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> revokedPartitions) {
            logger.debug("ConsumerRebalanceListener.onPartitionsRevoked on {}", revokedPartitions);

            partitions.stopProcessing(revokedPartitions);
            partitions.waitForHandlersToComplete(revokedPartitions, HANDLER_TIMEOUT_MILLIS);

            kafka.commitSync(partitions.offsetsToBeCommitted());

            partitions.removePartitions(revokedPartitions);
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> assignedPartitions) {
            logger.debug("ConsumerRebalanceListener.onPartitionsAssigned on {}", assignedPartitions);
            partitions.assignNewPartitions(assignedPartitions);
        }
    }
}
