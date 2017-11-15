package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

/**
 * Interface to be used with {@link com.sixt.service.framework.rpc.RpcClientBuilder}.withRetryBackOff()
 * to give the ability to do make the time duration between retries exponential.
 */
@FunctionalInterface
public interface RetryBackOffFunction {

    /**
     * Calculate the wait time between retries.  After the first attempt has been make, the
     * value of retryCounter here will be 0, then monotonically increasing.
     */
    Duration timeout(int retryCounter);

    default void execute(int retryCounter) {
        Duration timeout = timeout(retryCounter);
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Retry timeout cannot be null or negative.");
        }

        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeout.toMillis();

        // we are in a while loop here to protect against spurious interrupts
        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();
            if (now >= endTime) {
                break;
            }
            try {
                Thread.sleep(endTime - now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // we should probably quit if we are interrupted?
                return;
            }
        }
    }

}
