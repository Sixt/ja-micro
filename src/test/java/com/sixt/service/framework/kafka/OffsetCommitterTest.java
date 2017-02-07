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
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class OffsetCommitterTest {

    private KafkaConsumer<String, String> consumer;
    private OffsetCommitter committer;
    private Clock clock;

    @Before
    public void setup() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        consumer = mock(KafkaConsumer.class);
        committer = new OffsetCommitter(consumer, clock);
    }

    @Test
    public void partitionsAssigned() throws Exception {
        List<TopicPartition> partitions = new ArrayList<>();
        TopicPartition topicA = new TopicPartition("a", 1);
        TopicPartition topicB = new TopicPartition("b", 2);
        partitions.add(topicA);
        partitions.add(topicB);
        when(consumer.position(topicA)).thenReturn(42L);
        when(consumer.position(topicB)).thenReturn(43L);
        committer.partitionsAssigned(partitions);
        committer.recommitOffsets();
        verify(consumer, times(2)).position(any(TopicPartition.class));
        //it's not time to work yet
        verifyNoMoreInteractions(consumer);
        when(clock.instant()).thenReturn(Instant.now().plus(OffsetCommitter.IDLE_DURATION).plusMillis(1000));
        committer.recommitOffsets();
        //now it's time to work...
        verify(consumer, times(2)).commitSync(any(Map.class));
        committer.partitionsRevoked(partitions);
        assertThat(committer.offsetData).isEmpty();
    }

    @Test
    public void offsetGetsCommitted() throws Exception {
        committer.offsetCommitted(Collections.singletonMap(new TopicPartition("a", 1),
                new OffsetAndMetadata(333L)));
        committer.recommitOffsets();
        verifyNoMoreInteractions(consumer);
        when(clock.instant()).thenReturn(Instant.now().plus(OffsetCommitter.IDLE_DURATION).plusMillis(1000));
        committer.recommitOffsets();
        //now it's time to work...
        verify(consumer, times(1)).commitSync(any(Map.class));
    }
}
