package com.sixt.service.framework.kafka;

import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * We discovered that the default partition strategy in the java client is
 * different than what is used in librdkafka, which is used by several other
 * clients, so we now have aligned on using the xxHash strategy.
 */
public class SixtPartitioner implements Partitioner {

    private final XXHash32 xxHasher;
    private final int SEED = 0x9747b28c;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public SixtPartitioner() {
        XXHashFactory factory = XXHashFactory.fastestInstance();
        xxHasher = factory.hash32();
    }

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        if (keyBytes == null) {
            int nextValue = roundRobin.getAndIncrement();
            List<PartitionInfo> availablePartitions = cluster.availablePartitionsForTopic(topic);
            if (availablePartitions.size() > 0) {
                int part = Utils.toPositive(nextValue) % availablePartitions.size();
                return availablePartitions.get(part).partition();
            } else {
                // no partitions are available, give a non-available partition
                return Utils.toPositive(nextValue) % numPartitions;
            }
        } else {
            // hash the keyBytes to choose a partition
            return Utils.toPositive(xxHasher.hash(keyBytes, 0, keyBytes.length, SEED)) % numPartitions;
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }

}
