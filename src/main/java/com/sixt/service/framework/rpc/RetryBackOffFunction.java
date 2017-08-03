package com.sixt.service.framework.rpc;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public interface RetryBackOffFunction {

    AtomicBoolean shouldContinueWaitingFlag = new AtomicBoolean(false);
    AtomicLong pauseStartedAt = new AtomicLong(new Date().getTime());

    Duration timeout(int retryCounter);

    default void execute(int retryCounter) {

        Duration timeout = null;
        try {
            getClass().getMethod("timeout", (Class<?>[]) null);
            timeout = timeout(retryCounter);
        } catch (NoSuchMethodException e) {
            timeout = new ExponentialRetryBackOff().timeout(retryCounter);
        }

        if (timeout != null && !timeout.isNegative()) {
            shouldContinueWaitingFlag.set(true);
            while (!Thread.currentThread().isInterrupted() && shouldContinueWaitingFlag.get()) {
                synchronized (shouldContinueWaitingFlag) {
                    // we are in a while loop here to protect against spurious interrupts
                    while (shouldContinueWaitingFlag.get()) {
                        try {
                            Long timeSpent = new Date().getTime() - pauseStartedAt.get();
                            shouldContinueWaitingFlag.set(timeSpent <= timeout.toMillis());
                            shouldContinueWaitingFlag.wait(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            // we should probably quit if we are interrupted?
                            return;
                        }
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Retry timeout cant be null or negative.");
        }
    }

    class ExponentialRetryBackOff
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
}