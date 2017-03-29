package com.sixt.service.framework.kafka.messaging;


public interface FailedMessageProcessor {

    void onFailedMessage(Message failed, Throwable failureCause);

}
