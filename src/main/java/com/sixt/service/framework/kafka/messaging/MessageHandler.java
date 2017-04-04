package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.messaging.Message;


public interface MessageHandler<T extends com.google.protobuf.Message> {

    void onMessage(Message<T> message, OrangeContext context);

}
