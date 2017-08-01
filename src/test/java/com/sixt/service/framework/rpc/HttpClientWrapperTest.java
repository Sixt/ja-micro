package com.sixt.service.framework.rpc;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.GoTimer;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpContentResponse;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.opentracing.Span;
import io.opentracing.Tracer;

public class HttpClientWrapperTest {

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

    @Test
    public void it_should_wait_between_retries()
        throws RpcCallException, InterruptedException, ExecutionException, TimeoutException {
        // Given
        OrangeContext orangeContext = new OrangeContext();
        when(loadBalancer.getHealthyInstance()).thenReturn(createServiceEndpoint());
        when(loadBalancer.getHealthyInstanceExclude(anyList())).thenReturn(createServiceEndpoint());

        when(rpcClient.getRetries()).thenReturn(10);
        when(rpcClient.getTimeout()).thenReturn(0);
        httpClientWrapper.setLoadBalancer(loadBalancer);
        when(rpcClientMetrics.getMethodTimer(anyString(), anyString()))
            .thenReturn(new GoTimer("timer"));
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(span);
        when(httpClient.newRequest(any(URI.class))).thenReturn(request);
        when(request.content(any(ContentProvider.class))).thenReturn(request);
        when(request.method(anyString())).thenReturn(request);
        when(request.timeout(anyLong(), any(TimeUnit.class))).thenReturn(request);
        when(request.send()).thenReturn(httpContentResponse);
        when(httpContentResponse.getStatus()).thenReturn(100);

        RpcCallException exception = mock(RpcCallException.class);
        when(exception.isRetriable()).thenReturn(true);
        when(decoder.decodeException(any(ContentResponse.class))).thenReturn(exception);

        //When
        HttpRequestWrapper httpRequestWrapper = httpClientWrapper.createHttpPost(rpcClient);
        httpClientWrapper.execute(
            httpRequestWrapper,
            decoder,
            orangeContext,
            Duration.ofSeconds(1)
        );
    }

    private HttpRequestWrapper createRequestWrapper() {
        return new HttpRequestWrapper(HttpMethod.POST.asString(), createServiceEndpoint());
    }

    private ServiceEndpoint createServiceEndpoint() {
        return new ServiceEndpoint(new ScheduledThreadPoolExecutor(2), "localhost:20001", "dc1");
    }
}