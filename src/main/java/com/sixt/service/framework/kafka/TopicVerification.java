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

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TopicVerification {

    private static final Logger logger = LoggerFactory.getLogger(TopicVerification.class);

    public boolean verifyTopicsExist(String kafkaBrokers, Set<String> requiredTopics,
                                     boolean checkPartitionCounts) {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBrokers);
        props.put("group.id", UUID.randomUUID().toString());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        KafkaConsumer consumer = new KafkaConsumer(props);
        try {
            Map<String, List<PartitionInfo>> topics = consumer.listTopics();

            Set<Integer> partitionCount = new HashSet<>();
            for (String requiredTopic : requiredTopics) {
                List<PartitionInfo> partitions = topics.get(requiredTopic);
                if (partitions == null) {
                    logger.info("Required kafka topic {} not present", requiredTopic);
                    return false;
                }
                partitionCount.add(partitions.size());
            }
            if (checkPartitionCounts && partitionCount.size() > 1) {
                logger.warn("Partition count mismatch in topics {}",
                        Arrays.toString(requiredTopics.toArray()));
                return false;
            }
            return true;
        } finally {
            consumer.close();
        }
    }

}
