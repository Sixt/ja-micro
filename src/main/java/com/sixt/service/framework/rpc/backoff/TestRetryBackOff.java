package com.sixt.service.framework.rpc.backoff;

import java.time.Duration;

public class TestRetryBackOff
    implements RetryBackOffFunction {

    @Override
    public Duration timeout(int retryCounter) {
        return null;
    }

    @Override
    public void execute(int retryCounter) {
        try {
            Thread.currentThread().sleep(timeout(retryCounter).toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
