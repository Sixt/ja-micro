package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.KafkaTopicInfo;
import com.sixt.service.framework.protobuf.MessagingEnvelope;

/**
 * Created by abjb on 3/23/17.
 */
public class MessageMetadata {

    public String getTopic() {
        return null;
    }

    public String getPartitioningKey() {
        return null;
    }

    public KafkaTopicInfo getTopicInfo() {
        return null;
    }

    public MessageType getType() {
        return null;
    }


    static MessageMetadata fromEnvelope(MessagingEnvelope envelope) {
        return new MessageMetadata();
    }

    public OrangeContext getContext() {
        // FIXME fill in e.g. correlation id
        return new OrangeContext();
    }
}
