package com.sixt.service.framework.kafka.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// TODO rename to partition processor to reflect the active nature OR have a processor that holds the queue and the dispatcher??
class PartitionProcessor {

    private static final int MAX_MESSAGES_IN_FLIGHT = 100;

    private final BlockingQueue<Runnable> deliveryTaskQueue;

    private MessageDispatcher dispatcher;
    private boolean terminated;

    PartitionProcessor() {
        deliveryTaskQueue = new LinkedBlockingQueue<>();
    }


    public boolean shouldPauseTopic() {
        // TODO some fancy error rate / circuit breaker stuff

        return numberOfUnprocessedMessages() > MAX_MESSAGES_IN_FLIGHT;
    }

    int numberOfUnprocessedMessages() {
        // A snapshot value
        return deliveryTaskQueue.size();
    }

    public long lastConsumedMessageOffset() {
        // A snapshot of the last offset consumed in this partition.
        return 0;
    }

    public void enqueue(ConsumerRecord<String, byte[]> record) {
        try {
            deliveryTaskQueue.put(dispatcher.taskFor(record));
        } catch (InterruptedException e) {
            // FIXME
        }
    }


    public BlockingQueue<Runnable> getQueue() {
        return deliveryTaskQueue;
    }

    public void markAsConsumed(long messageOffset) {

    }

    public void stop() {
        dispatcher.stopProcessing();
    }

    public void waitForHandlerToComplete(long timeoutMillis) {
    }

    public boolean isTerminated() {
        return terminated;
    }

    public boolean hasUncommittedMessages() {
        return false;
    }


    public long getCommitOffsetAndClear() {
        // TODO thread safety
        return lastConsumedMessageOffset() + 1;
    }

    public boolean isPaused() {
        return false;
    }

    public boolean shouldResume() {
        return false;
    }
}
