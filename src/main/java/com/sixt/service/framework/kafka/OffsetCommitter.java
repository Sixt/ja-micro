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
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Offsets for a partition of a topic for a given consumer group are only kept
 * for a limited time.  To ensure that we don't lose the offsets in inactive
 * partitions, we periodically recommit the offsets.
 */
public class OffsetCommitter {

    final static Duration IDLE_DURATION = Duration.ofHours(1);

    private final KafkaConsumer<String, String> consumer;
    private final Clock clock;
    protected final Map<TopicPartition, OffsetAndTime> offsetData;
    protected LocalDateTime lastUpdateTime;

    public OffsetCommitter(KafkaConsumer<String, String> consumer, Clock clock) {
        this.consumer = consumer;
        this.clock = clock;
        offsetData = new HashMap<>();
        lastUpdateTime = LocalDateTime.now(clock);
    }

    public void partitionsAssigned(Collection<TopicPartition> partitions) {
        if (partitions != null) {
            LocalDateTime now = LocalDateTime.now(clock);
            for (TopicPartition tp : partitions) {
                long offset = consumer.position(tp);
                offsetData.put(tp, new OffsetAndTime(offset, now));
            }
        }
    }

    public void partitionsRevoked(Collection<TopicPartition> partitions) {
        if (partitions != null) {
            for (TopicPartition tp : partitions) {
                offsetData.remove(tp);
            }
        }
    }

    public void offsetCommitted(Map<TopicPartition, OffsetAndMetadata> offsetMap) {
        if (offsetMap != null) {
            LocalDateTime now = LocalDateTime.now(clock);
            for (TopicPartition tp : offsetMap.keySet()) {
                OffsetAndMetadata offsetAndMetadata = offsetMap.get(tp);
                offsetData.put(tp, new OffsetAndTime(offsetAndMetadata.offset(), now));
            }
        }
    }

    public void recommitOffsets() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isAfter(lastUpdateTime.plus(IDLE_DURATION))) {
            for (TopicPartition tp : offsetData.keySet()) {
                OffsetAndTime offsetAndTime = offsetData.get(tp);
                if (now.isAfter(offsetAndTime.time.plus(IDLE_DURATION))) {
                    consumer.commitSync(Collections.singletonMap(tp,
                            new OffsetAndMetadata(offsetAndTime.offset)));
                    offsetAndTime.time = now;
                }
            }
            lastUpdateTime = now;
        }
    }

    private class OffsetAndTime {
        long offset;
        LocalDateTime time;

        OffsetAndTime(long offset, LocalDateTime time) {
            this.offset = offset;
            this.time = time;
        }
    }

}
