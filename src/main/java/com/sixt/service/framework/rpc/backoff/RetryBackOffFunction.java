package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;
import java.util.Date;

/**
 * Interface of a timeout on retry functionality to be used with {@link com.sixt.service.framework.rpc.RpcClientBuilder}
 * as a custom/default possibility to build one's network client in a way it makes timeouts between retry calls
 */
@FunctionalInterface
public interface RetryBackOffFunction {

    Duration timeout(int retryCounter);

    default void execute(int retryCounter) {
        Duration timeout = timeout(retryCounter);
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Retry timeout cant be null or negative.");
        }

        Long startedAt = new Date().getTime();

        // we are in a while loop here to protect against spurious interrupts
        while (!Thread.currentThread().isInterrupted() && ((new Date().getTime() - startedAt) <= timeout.toMillis())) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // we should probably quit if we are interrupted?
                return;
            }
        }
    }
}
