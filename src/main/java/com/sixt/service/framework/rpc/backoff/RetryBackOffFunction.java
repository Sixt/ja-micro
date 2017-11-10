package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

@FunctionalInterface
public interface RetryBackOffFunction {

    Duration timeout(int retryCounter);

    default void execute(int retryCounter) {
        Duration timeout = timeout(retryCounter);
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Retry timeout cant be null or negative.");
        }

        AtomicBoolean notOverYet = new AtomicBoolean(true);
        Long startedAt = new Date().getTime();

        synchronized (notOverYet) {
            // we are in a while loop here to protect against spurious interrupts
            while (!Thread.currentThread().isInterrupted() && notOverYet.get()) {
                try {
                    notOverYet.set((new Date().getTime() - startedAt) <= timeout.toMillis());
                    notOverYet.wait(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // we should probably quit if we are interrupted?
                    return;
                }
            }
        }
    }
}