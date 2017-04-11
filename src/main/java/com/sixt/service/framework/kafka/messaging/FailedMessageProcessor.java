package com.sixt.service.framework.kafka.messaging;


/**
 * A Strategy interface to allow exchangeable failure handling behaviour.
 *
 */
public interface FailedMessageProcessor {

    /**
     * This method decides if a failed message should be re-tried or not.
     *
     * It may block the current thread (which is calling the message handler) if a delay between retries is required.
     *
     * @param failed the failed message
     * @param failureCause the root cause of the failure
     * @return true if message delivery should be re-tried, false otherwise
     */
    boolean onFailedMessage(Message failed, Throwable failureCause);

}
