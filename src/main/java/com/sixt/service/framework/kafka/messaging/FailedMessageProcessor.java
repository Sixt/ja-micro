package com.sixt.service.framework.kafka.messaging;


public interface FailedMessageProcessor {

    // true if message should be re-delivered
    // may block if delay strategy is in place
    boolean onFailedMessage(Message failed, Throwable failureCause);

}
