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
        if (durationIsApplicable()) {
            shouldContinueWaitingFlag.set(true);
            waitCurrentThread();
        }
    }

    private void waitCurrentThread() {
        while (!Thread.currentThread().isInterrupted() && shouldContinueWaitingFlag.get()) {
            synchronized (shouldContinueWaitingFlag) {
                // we are in a while loop here to protect against spurious interrupts
                while (shouldContinueWaitingFlag.get()) {
                    try {
                        Long timeSpent = new Date().getTime() - pauseStartedAt.get();
                        shouldContinueWaitingFlag.set(timeSpent <= duration.toMillis());
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

    private boolean durationIsApplicable() {
        return duration != null && !duration.isNegative();
    }
}
