package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

/**
 *  Default implementation of Exponential wait on retry function
 *  that produces a timeout for (10000 ^ call_counter) milliseconds
 */
public final class DefaultExponentialBackOff
    extends ExponentialRetryBackOff {

    private static final Duration DEFAULT_BASE = Duration.ofSeconds(10);

    public DefaultExponentialBackOff() {
        super(DEFAULT_BASE);
    }
}
