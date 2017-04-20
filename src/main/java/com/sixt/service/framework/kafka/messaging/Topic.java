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

package com.sixt.service.framework.kafka.messaging;

import com.google.common.base.Strings;

import javax.validation.constraints.NotNull;

/**
 * Naming scheme for messaging:
 * <p>
 * topic = "inbox" ["_" inbox_name ] "-" service_name
 * service_name = kafka_topic_char
 * inbox_name = kafka_topic_char
 * <p>
 * kafka_topic_char = "[a-zA-Z0-9\\._\\-]"        // letters, numbers, ".", "_", "-"
 */
public final class Topic {

    private final String topic;

    public Topic(@NotNull String topicName) {
        this.topic = topicName;
    }

    public static Topic defaultServiceInbox(@NotNull String serviceName) {
        return serviceInbox(serviceName, "");
    }

    public static Topic serviceInbox(@NotNull String serviceName, String inboxName) {
        StringBuilder topic = new StringBuilder();
        topic.append("inbox");

        if (!Strings.isNullOrEmpty(inboxName)) {
            topic.append("_");
            topic.append(inboxName);
        }

        if (Strings.isNullOrEmpty(serviceName)) {
            throw new IllegalArgumentException("service name must not be null or empty");
        }

        topic.append("-");
        topic.append(serviceName);


        return new Topic(topic.toString());
    }

    public boolean isEmpty() {
        return Strings.isNullOrEmpty(topic);
    }

    @Override
    public String toString() {
        return topic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Topic topic1 = (Topic) o;

        return topic != null ? topic.equals(topic1.topic) : topic1.topic == null;
    }

    @Override
    public int hashCode() {
        return topic != null ? topic.hashCode() : 0;
    }
}
