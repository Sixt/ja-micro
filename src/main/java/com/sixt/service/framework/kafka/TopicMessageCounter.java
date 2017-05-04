/**
 * Copyright 2017 Sixt GmbH & Co. Autovermietung KG
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

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TopicMessageCounter {

    private static final Logger logger = LoggerFactory.getLogger(TopicMessageCounter.class);

    @SuppressWarnings("unchecked")
    public long getCount(String kafkaBrokers, String topic) {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBrokers);
        props.put("group.id", UUID.randomUUID().toString());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        KafkaConsumer consumer = new KafkaConsumer(props);
        try {
            @SuppressWarnings("unchecked")
            Map<String, List<PartitionInfo>> topics = consumer.listTopics();
            List<PartitionInfo> partitionInfos = topics.get(topic);
            if (partitionInfos == null) {
                logger.warn("Partition information was not found for topic {}", topic);
                return 0;
            } else {
                Collection<TopicPartition> partitions = new ArrayList<>();
                for (PartitionInfo partitionInfo : partitionInfos) {
                    partitions.add(new TopicPartition(topic, partitionInfo.partition()));
                }
                Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
                Map<TopicPartition, Long> endingOffsets = consumer.endOffsets(partitions);
                return diffOffsets(beginningOffsets, endingOffsets);
            }
        } finally {
            consumer.close();
        }
    }

    private long diffOffsets(Map<TopicPartition, Long> beginning, Map<TopicPartition, Long> ending) {
        long retval = 0;
        for (TopicPartition partition : beginning.keySet()) {
            Long beginningOffset = beginning.get(partition);
            Long endingOffset = ending.get(partition);
            if (beginningOffset != null && endingOffset != null) {
                retval += (endingOffset - beginningOffset);
            }
        }
        return retval;
    }

}
