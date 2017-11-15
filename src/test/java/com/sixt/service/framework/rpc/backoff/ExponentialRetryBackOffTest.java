package com.sixt.service.framework.rpc.backoff;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class ExponentialRetryBackOffTest {

    @Test
    @Parameters({"2, 0, 1", "2, 1, 2", "2, 5, 32", "10, 0, 1", "10, 2, 100", "10, 4, 10000"})
    public void verifyTimeout(int base, int retryCount, long expectedSeconds) {
        ExponentialRetryBackOff backoff = new ExponentialRetryBackOff(base);
        Duration duration = backoff.timeout(retryCount);
        assertThat(duration.toMillis()).isEqualTo(expectedSeconds * 1000);
    }

}
