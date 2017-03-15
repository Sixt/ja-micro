package com.sixt.service.framework.rpc;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RpcCallExceptionDecoderTest {

    @Test
    public void verifyExceptionToString() {
        Exception ex = new IllegalStateException("testing");
        String result = RpcCallExceptionDecoder.exceptionToString(ex);
        assertThat(result).contains("Exception: IllegalStateException Message: testing " +
                "Stacktrace: java.lang.IllegalStateException: testing");
    }

}
