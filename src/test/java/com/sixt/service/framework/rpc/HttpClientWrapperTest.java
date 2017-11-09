package com.sixt.service.framework.rpc;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.GoTimer;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpContentResponse;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class HttpClientWrapperTest {

    private static final int NUMBER_OF_RETRIES = 3;

    private ServiceProperties serviceProperties = mock(ServiceProperties.class);
    private HttpClient httpClient = mock(HttpClient.class);
    private RpcClientMetrics rpcClientMetrics = mock(RpcClientMetrics.class);
    private Tracer tracer = mock(Tracer.class);
    private RpcClient rpcClient = mock(RpcClient.class);
    private LoadBalancer loadBalancer = mock(LoadBalancer.class);
    private Request request = mock(Request.class);

    private Tracer.SpanBuilder spanBuilder = mock(Tracer.SpanBuilder.class);
    private Span span = mock(Span.class);

    private HttpContentResponse httpContentResponse = mock(HttpContentResponse.class);
    private JsonRpcCallExceptionDecoder decoder = mock(JsonRpcCallExceptionDecoder.class);

    private HttpClientWrapper httpClientWrapper
        = new HttpClientWrapper(serviceProperties, httpClient, rpcClientMetrics, tracer);

    @Before
    public void setup() throws InterruptedException, ExecutionException, TimeoutException {
        when(loadBalancer.getHealthyInstance()).thenReturn(createServiceEndpoint());
        when(loadBalancer.getHealthyInstanceExclude(anyListOf(ServiceEndpoint.class)))
            .thenReturn(createServiceEndpoint());

        when(rpcClient.getRetries()).thenReturn(NUMBER_OF_RETRIES);
        when(rpcClient.getTimeout()).thenReturn(0);
        httpClientWrapper.setLoadBalancer(loadBalancer);
        when(rpcClientMetrics.getMethodTimer(any(), any())).thenReturn(new GoTimer("timer"));
        when(tracer.buildSpan(any())).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(span);
        when(httpClient.newRequest(any(URI.class))).thenReturn(request);
        when(httpClient.newRequest(any(String.class))).thenReturn(request);
        when(request.content(any(ContentProvider.class))).thenReturn(request);
        when(request.method(anyString())).thenReturn(request);
        when(request.timeout(anyLong(), any(TimeUnit.class))).thenReturn(request);
        when(request.send()).thenReturn(httpContentResponse);
        when(httpContentResponse.getStatus()).thenReturn(100);
    }

    @Ignore //TODO: Alex Borlis, please fix up this test.
    @Test
    public void it_should_wait_between_retries()
        throws RpcCallException, InterruptedException, ExecutionException, TimeoutException {
        // Given
        OrangeContext orangeContext = new OrangeContext();
        RpcCallException exception = mock(RpcCallException.class);
        when(exception.isRetriable()).thenReturn(true);
        when(decoder.decodeException(any(ContentResponse.class))).thenReturn(exception);
        when(rpcClient.getRetryBackOffFunction())
            .thenReturn(retryCounter -> Duration.ofMillis(10));
        when(rpcClient.hasRetryBackOffFunction())
            .thenReturn(true);

        //When
        HttpRequestWrapper httpRequestWrapper = httpClientWrapper.createHttpPost(rpcClient);

        int exceptionsCatchTimes = 0;
        long startTime = new Date().getTime();

        try {
            httpClientWrapper.execute(
                httpRequestWrapper,
                decoder,
                orangeContext
            );
        } catch (RpcCallException e) {
            exceptionsCatchTimes++;
        }

        long timeSpentOnRetries = new Date().getTime() - startTime;

        verify(rpcClient, times(NUMBER_OF_RETRIES + 1)).getTimeout();
        //todo:: seriously? 12 times and 6. need to be refactored
        verify(rpcClient, times(8)).getRetries();
        verify(rpcClient, times(8)).getMethodName();
        verify(rpcClient, times(4)).getServiceName();
        Assert.assertTrue(timeSpentOnRetries >= NUMBER_OF_RETRIES * 10);
        Assert.assertEquals(1, exceptionsCatchTimes);
    }

    private ServiceEndpoint createServiceEndpoint() {
        return new ServiceEndpoint(new ScheduledThreadPoolExecutor(2), "localhost:20001", "dc1");
    }
}