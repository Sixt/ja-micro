/**
 * Copyright 2016-2017 Sixt GmbH & Co. Autovermietung KG
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.sixt.service.framework.kafka.messaging;

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

        if (!shouldRetry) {
            return fallbackStrategy.onFailedMessage(failed, failureCause);
        }

        return shouldRetry;
    }

    /**
     * This method can be overridden to specify custom behaviour.
     * <p>
     * The default implementation simply returns false (non-retryable) in all cases.
     *
     * @param failureCause The exception thrown when delivering the message.
     * @return true if the message delivery should be retried, false otherwise.
     */
    protected boolean isRecoverable(Throwable failureCause) {
        return false;
    }


}
