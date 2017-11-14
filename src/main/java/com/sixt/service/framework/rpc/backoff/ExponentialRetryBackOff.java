package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

/**
 * Exponential implementation or retry timeout function that takes a settled exponential base and produces
 * a (BASE_IN_MILLISECONDS ^ call_counter) milliseconds timeouts
 */
public class ExponentialRetryBackOff
    implements RetryBackOffFunction {

    private final Duration exponentialBase;

    public ExponentialRetryBackOff(final Duration base) {
        if (base == null) {
            throw new IllegalArgumentException(
                "Exponential step should not be null, otherwise use " + DefaultExponentialBackOff.class.getName());
        }

        exponentialBase = base;
    }

    @Override
    public final Duration timeout(final int retryCounter) {
        if (retryCounter == 0) {
            return Duration.ofMillis(0);
        } else {
            return Duration.ofMillis((long) Math.pow(exponentialBase.toMillis(), retryCounter));
        }
    }
}
