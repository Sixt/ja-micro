package com.sixt.service.framework.rpc.backoff;

/**
 *  Default implementation of exponential wait. This causes a sleep
 *  for (10 ^ call_counter) seconds between retries
 */
public final class DefaultExponentialBackOff extends ExponentialRetryBackOff {

    private static final float DEFAULT_BASE = 10;

    public DefaultExponentialBackOff() {
        super(DEFAULT_BASE);
    }

}
