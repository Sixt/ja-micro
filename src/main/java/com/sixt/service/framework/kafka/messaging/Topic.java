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

}
