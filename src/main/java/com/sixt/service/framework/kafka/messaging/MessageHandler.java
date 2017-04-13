package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.OrangeContext;


public interface MessageHandler<T extends com.google.protobuf.Message> {

    /**
     * Callback interface to hand over a message.
     *
     * Implementors need to consider that we have at least once deliery, i.e. messages may be delivered multiple times (and potentially out of order).
     * Thus, message handlers need to handle duplicate messages gracefully / be idempotent.
     *
     * @param message
     * @param context
     */
    void onMessage(Message<T> message, OrangeContext context);

}
