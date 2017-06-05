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

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.time.Clock;
import java.util.Map;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KafkaSubscriberTest {

    @Test
    public void subscriberLosesPartitionAssignment() {
        KafkaSubscriber<String> subscriber = new KafkaSubscriber<>(new MessageCallback(),
                "topic", "groupId", false,
                KafkaSubscriber.OffsetReset.Earliest, 1, 1, 1,
                5000, 5000);
        KafkaTopicInfo message1 = new KafkaTopicInfo("topic", 0, 1, null);
        KafkaTopicInfo message2 = new KafkaTopicInfo("topic", 0, 2, null);
        KafkaTopicInfo message3 = new KafkaTopicInfo("topic", 1, 1, null);
        KafkaTopicInfo message4 = new KafkaTopicInfo("topic", 1, 2, null);
        subscriber.consume(message1);
        subscriber.consume(message2);
        subscriber.consume(message3);
        subscriber.consume(message4);
        KafkaConsumer realConsumer = mock(KafkaConsumer.class);
        class ArgMatcher extends ArgumentMatcher<Map<TopicPartition, OffsetAndMetadata>> {
            @Override
            public boolean matches(Object arg) {
                Map<TopicPartition, OffsetAndMetadata> data = (Map<TopicPartition, OffsetAndMetadata>) arg;
                OffsetAndMetadata oam = data.values().iterator().next();
                return oam.offset() == 3;
            }
        }
        doThrow(new CommitFailedException()).when(realConsumer).commitSync(argThat(new ArgMatcher()));
        subscriber.realConsumer = realConsumer;
        subscriber.offsetCommitter = new OffsetCommitter(realConsumer, Clock.systemUTC());
        subscriber.consumeMessages();
    }

    private static class MessageCallback implements EventReceivedCallback<String> {
        @Override
        public void eventReceived(String message, KafkaTopicInfo topicInfo) {
        }
    }

}
