package com.sixt.service.framework.kafka.messaging;

import com.google.common.collect.Lists;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumer instances are Kafka clients that fetch records of (one or multiple partitions of) a topic.
 *
 * Consumers also handle the flow control (pausing/resuming busy partitions) as well as changes in the partition assignment.
 *
 * The topic a consumer subscribes to must have been created before. When starting a consumer, it will check Kafka if the topic
 * exists.
 *
 * Threading model:
 * The Consumer has a single thread polling Kafka and handing over raw records to PartitionProcessors for further processing.
 * There is one PartitionProcessor per partition.
 * A PartitionProcessor currently is single-threaded to keep the ordering guarantee on a partition.
 *
 * Consumer instances are created by the ConsumerFactory.
 */
public class Consumer {
    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);

    private final Topic topic;
    private final String consumerGroupId;
    private final KafkaConsumer<String, byte[]> kafka;
    private final AssignedPartitions partitions;
    private final ExecutorService consumerLoopExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    // TODO Regular task to commit all offsets to not loose the offset after inactivity for e.g. a day -> to refresh the retention on the internal committed offset topic

    // Build by ConsumerFactory
    Consumer(Topic topic, String consumerGroupId, String servers, PartitionProcessorFactory processorFactory) {
        this.topic = topic;
        this.consumerGroupId = consumerGroupId;

        // Kafka consumer set up
        Properties props = new Properties();
        props.put("bootstrap.servers", servers);
        props.put("group.id", consumerGroupId);
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", ByteArrayDeserializer.class.getName());

        // The heartbeat is send in the background by the client library
        props.put("heartbeat.interval.ms", "10000");
        props.put("session.timeout.ms", "30000");

        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");

        // This is the actual timeout for the ConsumerLoop thread before Kafka rebalances the group.
        props.put("max.poll.interval.ms", 10000);


        kafka = new KafkaConsumer<>(props);

        partitions = new AssignedPartitions(processorFactory);

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
        partitions.waitForHandlersToComplete(allPartitions, 500);
        kafka.commitSync(partitions.offsetsToBeCommitted());

        kafka.close();

        logger.info("Consumer in group {} for topic {} was shut down.", consumerGroupId, topic.toString());
    }


    class ConsumerLoop implements Runnable {

        @Override
        public void run() {
            try {
                kafka.subscribe(Lists.newArrayList(topic.toString()), new PartitionAssignmentChange());
                logger.info("Consumer in group {} subscribed to topic {}", consumerGroupId, topic.toString());
            } catch (Exception unexpected){
                logger.error("Dead consumer in group {}: Cannot subscribe to topic {}", consumerGroupId, topic.toString(), unexpected);
                return;
            }

            try {
                while (!isStopped.get()) {
                    // Note that poll() may also execute the ConsumerRebalanceListener callbacks and may take substantially more time to return.
                    ConsumerRecords<String, byte[]> records = kafka.poll(300);

                    partitions.enqueue(records);
                    kafka.commitSync(partitions.offsetsToBeCommitted());

                    kafka.pause(partitions.partitionsToBePaused());
                    kafka.resume(partitions.partitionsToBeResumed());
                }
            } catch (Throwable unexpectedError) {
                logger.error("Unexpected exception in main consumer loop (group="+ consumerGroupId + " ,topic=" + topic.toString() + "). Consumer now dead.", unexpectedError);

                // Try to close the connection to Kafka
                kafka.close();

                // Let the thread and the consumer die. Kafka needs to rebalance the group.
                // Anyway, this points to a serious bug that needs to be fixed.
                throw unexpectedError;
            }
        }
    }


    class PartitionAssignmentChange implements ConsumerRebalanceListener {
        // From the documentation:
        // 1. This callback will execute in the user thread as part of the {@link Consumer#poll(long) poll(long)} call whenever partition assignment changes.
        // 2. It is guaranteed that all consumer processes will invoke {@link #onPartitionsRevoked(Collection) onPartitionsRevoked} prior to
        //    any process invoking {@link #onPartitionsAssigned(Collection) onPartitionsAssigned}.

        // Note that when Kafka rebalances partitions, all currently assigned partitions are revoked and then the remaining partitions are newly assigned.

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> revokedPartitions) {
            logger.debug("ConsumerRebalanceListener.onPartitionsRevoked on {}", revokedPartitions);

            partitions.stopProcessing(revokedPartitions);
            partitions.waitForHandlersToComplete(revokedPartitions, 500);

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
