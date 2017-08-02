package com.sixt.service.framework.rpc;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class Timeout {

    private final AtomicBoolean shouldContinueWaitingFlag = new AtomicBoolean(false);
    private final AtomicLong pauseStartedAt = new AtomicLong(new Date().getTime());
    private final Duration duration;

    Timeout(final Duration pauseDuration) {
        duration = pauseDuration;
    }

    void execute() {
        if (retryTimeoutDurationApplicable()) {
            shouldContinueWaitingFlag.set(true);
            sleepMe();
        }
    }

    private void sleepMe() {
        while (!Thread.currentThread().isInterrupted() && shouldContinueWaitingFlag.get()) {
            synchronized (shouldContinueWaitingFlag) {
                // we are in a while loop here to protect against spurious interrupts
                while (shouldContinueWaitingFlag.get()) {
                    try {
                        shouldContinueWaitingFlag.set(
                            (new Date().getTime() - pauseStartedAt.get()) <= duration.toMillis());
                        shouldContinueWaitingFlag.wait(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // we should probably quit if we are interrupted?
                        return;
                    }
                }
            }
        }
    }

    private boolean retryTimeoutDurationApplicable() {
        return duration != null && !duration.isNegative();
    }
}
