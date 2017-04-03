package com.sixt.service.framework.kafka.messaging;

import java.util.concurrent.TimeUnit;


// Thread safety: single thread use
public class DelayAndRetryOnRecoverableErrors implements FailedMessageProcessor {

    private final FailedMessageProcessor fallbackStrategy;
    private final RetryDelayer retryStrategy;

    Message lastFailedMessage = null;

    public DelayAndRetryOnRecoverableErrors(FailedMessageProcessor fallbackStrategy, RetryDelayer retryStrategy) {
        this.fallbackStrategy = fallbackStrategy;
        this.retryStrategy = retryStrategy;
    }

    @Override
    public boolean onFailedMessage(Message failed, Throwable failureCause) {
        if (!isRecoverable(failureCause)) {
            return fallbackStrategy.onFailedMessage(failed, failureCause);
        }

        // we have a new failure case
        if (failed != lastFailedMessage) { // reference equals by intention
            lastFailedMessage = failed;
            retryStrategy.reset();
        }

        // blocks the message handler thread -> flow control may pause the partition
        boolean shouldRetry = retryStrategy.delay();

        if(!shouldRetry) {
            return fallbackStrategy.onFailedMessage(failed, failureCause);
        }

        return shouldRetry;
    }

    private boolean isRecoverable(Throwable failureCause) {

        // TODO make interchangeable strategy

        // FIXME
        return false;
    }

}
