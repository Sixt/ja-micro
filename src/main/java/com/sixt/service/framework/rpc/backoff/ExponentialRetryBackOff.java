package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

/**
 * This causes a sleep for (exponentialBase ^ retryCounter) seconds between retries
 */
public class ExponentialRetryBackOff implements RetryBackOffFunction {

    private final float exponentialBase;

    public ExponentialRetryBackOff(final float base) {
        if (base <= 1) {
            throw new IllegalArgumentException("Base cannot be <= 1");
        }
        exponentialBase = base;
    }

    @Override
    public final Duration timeout(final int retryCounter) {
        return Duration.ofSeconds((long) Math.pow(exponentialBase, retryCounter));
    }

}
