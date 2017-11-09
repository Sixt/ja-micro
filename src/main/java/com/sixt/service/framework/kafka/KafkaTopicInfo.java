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

import org.apache.kafka.common.TopicPartition;

public class KafkaTopicInfo {

    protected String topic;
    protected int partition;
    protected long offset;
    protected String key;

    public KafkaTopicInfo(String topic, int partition, long offset, String key) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.key = key;
    }

    public String getTopic() {
        return topic;
    }

    public int getPartition() {
        return partition;
    }

    public long getOffset() {
        return offset;
    }

    public TopicPartition getTopicPartition() {
        return new TopicPartition(topic, partition);
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KafkaTopicInfo topicInfo = (KafkaTopicInfo) o;

        if (getPartition() != topicInfo.getPartition()) return false;
        if (getOffset() != topicInfo.getOffset()) return false;
        if (getTopic() != null ? !getTopic().equals(topicInfo.getTopic()) : topicInfo.getTopic() != null) return false;
        return getKey() != null ? getKey().equals(topicInfo.getKey()) : topicInfo.getKey() == null;
    }

    @Override
    public int hashCode() {
        int result = getTopic() != null ? getTopic().hashCode() : 0;
        result = 31 * result + getPartition();
        result = 31 * result + (int) (getOffset() ^ (getOffset() >>> 32));
        result = 31 * result + (getKey() != null ? getKey().hashCode() : 0);
        return result;
    }

}
