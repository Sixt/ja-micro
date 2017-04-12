package com.sixt.service.framework.kafka.messaging;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Delay with a fixed interval up to a maximum total delay.
 *
 * The maximum total delay is simply the sum of all delays, but not the real time spend in delaying.
 *
 */
public class SimpleRetryDelayer implements RetryDelayer {

    private final long delayIntervallMillis;
    private final long maximumDelayMillis;

    private final AtomicLong accumulatedDelay = new AtomicLong(0);

    public SimpleRetryDelayer(long delayIntervallMillis, long maximumDelayMillis) {
        this.delayIntervallMillis = delayIntervallMillis;
        this.maximumDelayMillis = maximumDelayMillis;
    }


    @Override
    public boolean delay() {
        long total = accumulatedDelay.addAndGet(delayIntervallMillis);

        if (total >= maximumDelayMillis) {
            return false;
        }

        try {
            Thread.sleep(delayIntervallMillis);
        } catch (InterruptedException ignored) {

        }

        return true;
    }

    @Override
    public void reset() {
        accumulatedDelay.set(0);
    }
}
