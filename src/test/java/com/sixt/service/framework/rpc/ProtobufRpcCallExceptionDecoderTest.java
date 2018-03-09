package com.sixt.service.framework.rpc;

import com.google.common.net.MediaType;
import junitparams.Parameters;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.client.HttpContentResponse;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.google.common.net.MediaType.ANY_TYPE;
import static com.sixt.service.framework.rpc.RpcCallException.Category.InternalServerError;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class ProtobufRpcCallExceptionDecoderTest {

    private final ProtobufRpcCallExceptionDecoder decoder = new ProtobufRpcCallExceptionDecoder();

    @Test
    public void decodeException_nullContentPassed_InternalServerErrorShouldBeReturned()
            throws RpcCallException {
        byte[] content = null;
        RpcCallException exception = decoder.decodeException(response(content));

        assertThat(exception.getCategory()).isEqualTo(InternalServerError);
        assertThat(exception.getMessage()).isEqualTo("Empty response received");
    }

    @Test
    public void decodeException_zeroLengthContentPassed_InternalServerErrorShouldBeReturned()
            throws RpcCallException {
        byte[] content = new byte[0];
        RpcCallException exception = decoder.decodeException(response(content));

        assertThat(exception.getCategory()).isEqualTo(InternalServerError);
        assertThat(exception.getMessage()).isEqualTo("Empty response received");
    }

    private ContentResponse response(byte[] content) {
        return new HttpContentResponse(null, content, ANY_TYPE.type(), UTF_8.name());
    }

}