package com.sixt.service.framework.kafka;

import com.sixt.service.framework.OrangeContext;

/**
 * Created by abjb on 3/23/17.
 */
public interface MessageHandler<T extends com.google.protobuf.Message> {

    void onMessage(Message<T> msg, OrangeContext context);

}
