package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

public final class DefaultExponentialBackOff implements RetryBackOffFunction {

    private static final double DEFAULT_BASE = 10d;

    @Override
    public Duration timeout(final int retryCounter) {
        if (retryCounter == 0) {
            return Duration.ofMillis(0);
        } else {
            return Duration.ofMillis((long) Math.pow(DEFAULT_BASE, retryCounter));
        }
    }
}
