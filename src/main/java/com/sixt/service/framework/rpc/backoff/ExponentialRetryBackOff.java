package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

public final class ExponentialRetryBackOff
    implements RetryBackOffFunction {

    private final Duration base;

    public ExponentialRetryBackOff(final Duration exponentialBase) {
        if (exponentialBase == null) {
            throw new IllegalArgumentException(
                "Exponential step should not be null, otherwise use " + DefaultExponentialBackOff.class.getName());
        }

        base = exponentialBase;
    }

    @Override
    public Duration timeout(final int retryCounter) {
        if (retryCounter == 0) {
            return Duration.ofMillis(0);
        } else {
            return Duration.ofMillis((long) Math.pow(base.toMillis(), retryCounter));
        }
    }
}
