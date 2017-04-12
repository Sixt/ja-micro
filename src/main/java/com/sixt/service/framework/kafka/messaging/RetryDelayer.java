package com.sixt.service.framework.kafka.messaging;



public interface RetryDelayer {

    // returns true if we should still retrying
    boolean delay();

    // reset the previous attempt if we have a new retry-case
    void reset();
}
