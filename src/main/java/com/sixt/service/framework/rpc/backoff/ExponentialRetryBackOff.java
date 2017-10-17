package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

public class ExponentialRetryBackOff
    implements RetryBackOffFunction {

    private final Duration exponentialStep;

    ExponentialRetryBackOff(final Duration exponentialStep) {
        this.exponentialStep = exponentialStep;
    }

    ExponentialRetryBackOff() {
        this(null);
    }

    @Override
    public Duration timeout(final int retryCounter) {
        if (retryCounter == 0) {
            return Duration.ofMillis(0);
        } else {
            return Duration.ofMillis((long) Math.pow(
                exponentialStep == null ? 10d : exponentialStep.toMillis(), retryCounter));
        }
    }
}
