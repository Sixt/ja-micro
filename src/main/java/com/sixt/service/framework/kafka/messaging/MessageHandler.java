package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.messaging.Message;

/**
 * Created by abjb on 3/23/17.
 */
public interface MessageHandler<T extends com.google.protobuf.Message> {

    void onMessage(Message<T> msg, OrangeContext context);

}
