package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public interface RetryBackOffFunction {

    AtomicBoolean shouldContinueWaitingFlag = new AtomicBoolean(false);
    AtomicLong pauseStartedAt = null;

    Duration timeout(int retryCounter);

    default void execute(int retryCounter) {
        if (pauseStartedAt == null) {
            pauseStartedAt.set(new Date().getTime());
        }

        Duration timeout = timeout(retryCounter);
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Retry timeout cant be null or negative.");
        }

        shouldContinueWaitingFlag.set(true);
        while (!Thread.currentThread().isInterrupted() && shouldContinueWaitingFlag.get()) {
            synchronized (shouldContinueWaitingFlag) {
                // we are in a while loop here to protect against spurious interrupts
                while (shouldContinueWaitingFlag.get()) {
                    try {
                        Long timeSpent = new Date().getTime() - pauseStartedAt.get();
                        shouldContinueWaitingFlag.set(timeSpent <= timeout.toMillis());
                        shouldContinueWaitingFlag.wait(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // we should probably quit if we are interrupted?
                        return;
                    }
                }
            }
        }
    }
}