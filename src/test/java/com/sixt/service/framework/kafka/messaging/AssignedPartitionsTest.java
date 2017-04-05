package com.sixt.service.framework.kafka.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class AssignedPartitionsTest {

    public static final String TOPIC = "aTopic";
    public static final String KEY = "aKey";
    public static final EmptyMessage EMPTY_MESSAGE = EmptyMessage.getDefaultInstance();

    @Test
    public void enqueuingARecordForANewPartitionShouldCreateAProcessor() {
        int partitionId = 9;

        ConsumerRecords<String, byte[]> records = givenATestRecord(TOPIC, partitionId, 42);
        PartitionProcessorFactory ppf = processorFactory();


        AssignedPartitions ap = new AssignedPartitions(ppf);
        assertTrue(ap.allPartitions().isEmpty());

        ap.enqueue(records);
        Set<TopicPartition> partitions = ap.allPartitions();

        assertEquals(1, partitions.size());
        TopicPartition partition = partitions.iterator().next();

        assertEquals(TOPIC, partition.topic());
        assertEquals(partitionId, partition.partition());

    }

    @Test
    public void assignNewPartitonsCreatesProcessors() {
        PartitionProcessorFactory ppf = processorFactory();
        AssignedPartitions ap = new AssignedPartitions(ppf);

        Collection<TopicPartition> newPartitions = new ArrayList<>();
        newPartitions.add(new TopicPartition(TOPIC, 3));
        newPartitions.add(new TopicPartition(TOPIC, 1));
        newPartitions.add(new TopicPartition(TOPIC, 99));

        ap.assignNewPartitions(newPartitions);

        assertFalse(ap.allPartitions().isEmpty());
        Set<TopicPartition> partitions = ap.allPartitions();
        assertEquals(3, partitions.size());
        assertTrue(partitions.containsAll(newPartitions));
    }


    private ConsumerRecords<String, byte[]> givenATestRecord(String topic, int partition, long offset) {
        ConsumerRecord<String, byte[]> aRecord = new ConsumerRecord<String, byte[]>(topic, partition, offset, KEY, EMPTY_MESSAGE.toByteArray());
        List<ConsumerRecord<String, byte[]>> recordsForPartition = new ArrayList<>();
        recordsForPartition.add(aRecord);

        Map<TopicPartition, List<ConsumerRecord<String, byte[]>>> topicRecordMap = new HashMap<>();
        topicRecordMap.put(new TopicPartition(aRecord.topic(), aRecord.partition()), recordsForPartition);


        return new ConsumerRecords<String, byte[]>(topicRecordMap);
    }

    private PartitionProcessorFactory processorFactory() {
        TypeDictionary typeDictionary = new TypeDictionary(new HashMap<>(), new HashMap<>());
        FailedMessageProcessor failedMessageProcessor = new DiscardFailedMessages();
        return new PartitionProcessorFactory(typeDictionary, failedMessageProcessor);
    }

}
