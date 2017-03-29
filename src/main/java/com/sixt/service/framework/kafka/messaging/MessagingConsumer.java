package com.sixt.service.framework.kafka.messaging;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by abjb on 3/28/17.
 */
public class MessagingConsumer {

    // TODO subscription etc.
    private KafkaConsumer<String, byte[]> kafka;
    private AssignedPartitions partitions;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    // TODO Regular task to commit all offsets to not loose the offset after inactivty for e.g. a day
    // -> to refresh the retention on the internal committed offset topic


    class ConsumerLoop implements Runnable {
        @Override
        public void run() {
            while (!isStopped.get()) {
                // Note that poll() may also execute the ConsumerRebalanceListener callbacks and may take substantially more time to return.
                ConsumerRecords<String, byte[]> records = kafka.poll(300);

                partitions.enqueue(records);
                kafka.commitSync(partitions.offsetsToBeCommitted());

                kafka.pause(partitions.partitionsToBePaused());
                kafka.resume(partitions.partitionsToBeResumed());
            }
        }
    }


    class PartitionAssignmentChange implements ConsumerRebalanceListener {
        // From the documentation:
        // 1. This callback will execute in the user thread as part of the {@link Consumer#poll(long) poll(long)} call whenever partition assignment changes.
        // 2. It is guaranteed that all consumer processes will invoke {@link #onPartitionsRevoked(Collection) onPartitionsRevoked} prior to
        //    any process invoking {@link #onPartitionsAssigned(Collection) onPartitionsAssigned}.

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> revokedPartitions) {
            partitions.stopProcessing(revokedPartitions);
            partitions.waitForHandlersToComplete(revokedPartitions, 500);

            kafka.commitSync(partitions.offsetsToBeCommitted());

            partitions.removePartitions(revokedPartitions);
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> assignedPartitions) {
            // TODO do we get a notification on first subscription?
            partitions.assignNewPartitions(assignedPartitions);
        }
    }

}
