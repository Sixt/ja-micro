/**
 * Copyright 2016-2017 Sixt GmbH & Co. Autovermietung KG
 * Licensed under the Apache License, Version 2.0 (the "License"); you may 
 * not use this file except in compliance with the License. You may obtain a 
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 */

package com.sixt.service.framework.jetty;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.sixt.service.framework.FeatureFlags;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.json.JsonRpcRequest;
import com.sixt.service.framework.metrics.GoTimer;
import com.sixt.service.framework.protobuf.RpcEnvelope;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.servicetest.mockservice.MockHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class JsonHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonHandlerTest.class);

    private JsonHandler servlet;
    private MethodHandlerDictionary handlerDictionary;
    private MetricRegistry metricRegistry;
    private RpcHandlerMetrics handlerMetrics;

    @Before
    public void setup() throws RpcCallException {
        handlerDictionary = new MethodHandlerDictionary();
        handlerDictionary.put("a", null);
        ServiceMethodHandlerUnderTest mockHandlerThrowsRpcCallEx = new ServiceMethodHandlerUnderTest();

        handlerDictionary.put("jsonRpcWithException", mockHandlerThrowsRpcCallEx);

        metricRegistry = mock(MetricRegistry.class);
        when(metricRegistry.counter(anyString())).thenReturn(mock(Counter.class));
        when(metricRegistry.timer(anyString())).thenReturn(mock(Timer.class));

        handlerMetrics = mock(RpcHandlerMetrics.class);
        when(handlerMetrics.getMethodTimer(any(), any(), any())).thenReturn(mock(GoTimer.class));

        servlet = new JsonHandler(handlerDictionary, metricRegistry, handlerMetrics, new ServiceProperties(), null);
    }

    @Test
    public void testParseRpcRequestNoId() {
        String input = "{\"service\":\"x\",\"method\":\"a\",\"params\":[{\"data\":\"\"}]}";
        JsonRpcRequest request = servlet.parseRpcRequest(input);
        assertThat(request.getId()).isNull();
    }

    @Test
    public void testParseRpcRequestNullId() {
        String input = "{\"service\":\"x\",\"method\":\"a\",\"params\":[{\"data\":\"\"}],\"id\":null}";
        JsonRpcRequest request = servlet.parseRpcRequest(input);
        assertThat(request.getId()).isInstanceOf(JsonNull.class);
    }

    @Test
    public void testParseRpcRequestNumericId() {
        String input = "{\"service\":\"x\",\"method\":\"a\",\"params\":[{\"data\":\"\"}],\"id\":123456789}";
        JsonRpcRequest request = servlet.parseRpcRequest(input);
        JsonElement idElement = request.getId();

        assertThat(idElement.toString()).isEqualTo("123456789");
    }

    @Test
    public void testParseRpcRequestStringNumericId() {
        String input = "{\"service\":\"x\",\"method\":\"a\",\"params\":[{\"data\":\"\"}],\"id\":\"234234\"}";
        JsonRpcRequest request = servlet.parseRpcRequest(input);
        JsonElement idElement = request.getId();

        assertThat(idElement.toString()).isEqualTo("\"234234\"");
    }

    @Test
    public void testParseRpcRequestStringId() {
        String input = "{\"service\":\"x\",\"method\":\"a\",\"params\":[{\"data\":\"\"}],\"id\":\"dead-beef\"}";
        JsonRpcRequest request = servlet.parseRpcRequest(input);
        JsonElement idElement = request.getId();

        assertThat(idElement.toString()).isEqualTo("\"dead-beef\"");
    }

    @Test
    public void testRpcCallExceptionJsonRpc() throws IOException {
        String input = "{\"service\":\"x\",\"method\":\"jsonRpcWithException\",\"params\":[{}],\"id\":\"dead-beef\"}";
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(MockHttpServletResponse.class);

        BufferedReader readerFromRequest = new BufferedReader(new StringReader(input));
        when(request.getReader()).thenReturn(readerFromRequest);
        CharArrayWriter charArryWriter = new CharArrayWriter(512);
        PrintWriter writer = new PrintWriter(charArryWriter);
        when(response.getWriter()).thenReturn(writer);
        doCallRealMethod().when(response).setStatus(anyInt());
        doCallRealMethod().when(response).getStatus();

        servlet.doPost(request, response);

        String responseAsString = charArryWriter.toString();
        LOGGER.debug(responseAsString);
        assertThat(responseAsString).contains("no p4s5!");
        assertThat(responseAsString).matches(Pattern.compile(".*retriable.+true.*"));
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    public void testRpcCallExceptionJsonRpcWithHttpExposed() throws IOException {
        String input = "{\"service\":\"x\",\"method\":\"jsonRpcWithException\",\"params\":[{}],\"id\":\"dead-beef\"}";
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(MockHttpServletResponse.class);

        BufferedReader readerFromRequest = new BufferedReader(new StringReader(input));
        when(request.getReader()).thenReturn(readerFromRequest);
        CharArrayWriter charArryWriter = new CharArrayWriter(512);
        PrintWriter writer = new PrintWriter(charArryWriter);
        when(response.getWriter()).thenReturn(writer);
        doCallRealMethod().when(response).setStatus(anyInt());
        doCallRealMethod().when(response).getStatus();

        ServiceProperties props = new ServiceProperties();
        props.addProperty(FeatureFlags.FLAG_EXPOSE_ERRORS_HTTP, "true");
        servlet = new JsonHandler(handlerDictionary, metricRegistry, handlerMetrics, props, null);
        servlet.doPost(request, response);

        String responseAsString = charArryWriter.toString();
        LOGGER.debug(responseAsString);
        assertThat(responseAsString).contains("no p4s5!");
        assertThat(responseAsString).matches(Pattern.compile(".*retriable.+true.*"));
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void doPost_NotValidJson_ErrorMessageBadRequest() throws IOException {
        // given
        final String json = "xxxx";
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(MockHttpServletResponse.class);

        BufferedReader readerFromRequest = new BufferedReader(new StringReader(json));
        when(request.getReader()).thenReturn(readerFromRequest);
        CharArrayWriter charArryWriter = new CharArrayWriter(512);
        PrintWriter writer = new PrintWriter(charArryWriter);
        when(response.getWriter()).thenReturn(writer);
        doCallRealMethod().when(response).setStatus(anyInt());
        doCallRealMethod().when(response).getStatus();

        // when
        servlet.doPost(request, response);

        // then
        final String responseAsString = charArryWriter.toString();
        LOGGER.debug(responseAsString);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    public void doPost_NotValidJson_ErrorMessageBadRequestWithHttpExposed() throws IOException {
        // given
        final String json = "xxxx";
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(MockHttpServletResponse.class);

        BufferedReader readerFromRequest = new BufferedReader(new StringReader(json));
        when(request.getReader()).thenReturn(readerFromRequest);
        CharArrayWriter charArryWriter = new CharArrayWriter(512);
        PrintWriter writer = new PrintWriter(charArryWriter);
        when(response.getWriter()).thenReturn(writer);
        doCallRealMethod().when(response).setStatus(anyInt());
        doCallRealMethod().when(response).getStatus();

        // when
        ServiceProperties props = new ServiceProperties();
        props.addProperty(FeatureFlags.FLAG_EXPOSE_ERRORS_HTTP, "true");
        servlet = new JsonHandler(handlerDictionary, metricRegistry, handlerMetrics, props, null);
        servlet.doPost(request, response);

        // then
        final String responseAsString = charArryWriter.toString();
        LOGGER.debug(responseAsString);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    public void parseRpcRequest_NotJson_ThrowIllegalArgumentException() {
        // given
        final String json = "xxxx";

        // when
        Throwable thrown = catchThrowable(() -> {
            servlet.parseRpcRequest(json);
        });
        LOGGER.debug(thrown.getMessage());
        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(thrown).hasMessageContaining("Not a JSON");
    }

    @Test
    public void parseRpcRequest_NotValidJson_ThrowIllegalArgumentException() {
        // given
        final String json = "xxxx}";

        // when
        Throwable thrown = catchThrowable(() -> {
            servlet.parseRpcRequest(json);
        });
        LOGGER.debug(thrown.getMessage());
        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(thrown).hasMessageContaining("Malformed");
    }

    @Test
    public void parseRpcRequest_NoMethodNameGiven_ThrowIllegalArgumentExecption() {
        // given
        final String json = "{\"service\":\"x\",\"params\":[{\"data\":\"\"}],\"id\":\"dead-beef\"}";

        // when
        Throwable thrown = catchThrowable(() -> {
            servlet.parseRpcRequest(json);
        });

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(thrown).hasMessage("Missing method name");
    }

    class ServiceMethodHandlerUnderTest implements com.sixt.service.framework.ServiceMethodHandler<RpcEnvelope.Request, RpcEnvelope.Response>{
        @Override
        public RpcEnvelope.Response handleRequest(RpcEnvelope.Request request, OrangeContext ctx) throws RpcCallException {
            throw new RpcCallException(RpcCallException.Category.InternalServerError, "no p4s5!");
        }
    };

}