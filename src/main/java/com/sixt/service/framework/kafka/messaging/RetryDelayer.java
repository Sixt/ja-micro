package com.sixt.service.framework.kafka.messaging;



public interface RetryDelayer {

    // returns true if we should still retrying
    boolean delay();

    void reset();
}
