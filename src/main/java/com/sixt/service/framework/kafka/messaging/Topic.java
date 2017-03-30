package com.sixt.service.framework.kafka.messaging;

public class Topic {

    private final String topic;

    public Topic(String topicName) {
        this.topic = topicName;
    }

    public static Topic defaultServiceInbox(String serviceName) {
        // TODO fancy naming scheme
        return new Topic("inbox-" + serviceName);
    }

    public static Topic serviceInbox(String serviceName, String inboxName) {
        // TODO fancy naming scheme
        return new Topic("inbox-" + serviceName + "-" + inboxName);
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
