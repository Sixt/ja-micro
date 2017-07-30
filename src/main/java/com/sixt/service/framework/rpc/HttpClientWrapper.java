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

package com.sixt.service.framework.rpc;

import com.google.inject.Inject;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.GoTimer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.sixt.service.framework.FeatureFlags.shouldExposeErrorsToHttp;
import static net.logstash.logback.marker.Markers.append;

public class HttpClientWrapper {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientWrapper.class);

    protected ServiceProperties serviceProps;
    protected LoadBalancer loadBalancer;
    protected HttpClient httpClient;
    protected RpcClientMetrics rpcClientMetrics;
    protected RpcClient client;
    protected Tracer tracer;

    @Inject
    public HttpClientWrapper(ServiceProperties serviceProps, HttpClient httpClient,
                             RpcClientMetrics rpcClientMetrics, Tracer tracer) {
        this.serviceProps = serviceProps;
        this.httpClient = httpClient;
        this.rpcClientMetrics = rpcClientMetrics;
        this.tracer = tracer;
    }

    public HttpRequestWrapper createHttpPost(RpcClient client)
            throws RpcCallException {
        this.client = client;
        ServiceEndpoint instance = loadBalancer.getHealthyInstance();
        if (instance == null) {
            throw new RpcCallException(RpcCallException.Category.InternalServerError,
                    "No available instance of " + loadBalancer.getServiceName()).
                    withSource(serviceProps.getServiceName());
        }
        return new HttpRequestWrapper("POST", instance);
    }

    private HttpRequestWrapper createHttpPost(HttpRequestWrapper previous, List<ServiceEndpoint> triedEndpoints)
            throws RpcCallException {
        ServiceEndpoint instance = loadBalancer.getHealthyInstanceExclude(triedEndpoints);
        if (instance == null) {
            throw new RpcCallException(RpcCallException.Category.InternalServerError,
                    "RpcCallException calling " + loadBalancer.getServiceName() + ", no available instance").
                    withSource(serviceProps.getServiceName());
        }
        //TODO: There may still be a problem where retries are setting chunked encoding
        // or the content-length gets munged
        HttpRequestWrapper retval =  new HttpRequestWrapper("POST", instance);
        retval.setHeaders(previous.getHeaders());
        retval.setContentProvider(previous.getContentProvider());
        return retval;
    }

    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public ContentResponse execute(
        HttpRequestWrapper request,
        RpcCallExceptionDecoder decoder,
        OrangeContext orangeContext
    ) throws RpcCallException {
        return execute(request, decoder, orangeContext, null);
    }

    public ContentResponse execute(
        HttpRequestWrapper request,
        RpcCallExceptionDecoder decoder,
        OrangeContext orangeContext,
        final Duration retryTimeoutDuration
    ) throws RpcCallException {

        ContentResponse retval = null;
        Span span = null;
        List<ServiceEndpoint> triedEndpoints = new ArrayList<>();
        RpcCallException lastException = null;
        int lastStatusCode;
        int tryCount = 0;
        do {
            triedEndpoints.add(request.getServiceEndpoint());
            GoTimer methodTimer = getMethodTimer();
            long startTime = methodTimer.start();
            try {
                Marker logMarker = append("serviceMethod", request.getMethod())
                        .and(append("serviceEndpoint", request.getServiceEndpoint()));
                logger.debug(logMarker,
                        "Sending http request to {}", request.getServiceEndpoint());
                if (tracer != null) {
                    SpanContext spanContext = null;
                    if (orangeContext != null) {
                        spanContext = orangeContext.getTracingContext();
                    }
                    if (spanContext != null) {
                        span = tracer.buildSpan(client.getMethodName()).asChildOf(spanContext).start();
                    } else {
                        span = tracer.buildSpan(client.getMethodName()).start();
                    }
                    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
                    Tags.PEER_SERVICE.set(span, loadBalancer.getServiceName());
                    if (orangeContext != null) {
                        span.setTag("correlation_id", orangeContext.getCorrelationId());
                    }
                    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapInjectAdapter(request.getHeaders()));
                }
                retval = request.newRequest(httpClient).timeout(client.getTimeout(),
                        TimeUnit.MILLISECONDS).send();
                logger.debug(logMarker, "Http send completed");
                lastStatusCode = retval.getStatus();
            } catch (TimeoutException timeout) {
                lastStatusCode = RpcCallException.Category.RequestTimedOut.getHttpStatus();
                lastException = new RpcCallException(RpcCallException.Category.RequestTimedOut, "Http-client timeout");
                //TODO: RequestTimedOut should be retried as long as there is time budget left
                logger.info(getRemoteMethod(), "Caught TimeoutException executing request");
            } catch (Exception ex) {
                lastStatusCode = RpcCallException.Category.InternalServerError.getHttpStatus();
                logger.debug(getRemoteMethod(), "Caught exception executing request", ex);
            }

            //content.length must always be > 0, because we have an envelope
            if (responseWasSuccessful(decoder, retval, lastStatusCode)) {
                if (span != null) {
                    Tags.HTTP_STATUS.set(span, lastStatusCode);
                    span.finish();
                }
                methodTimer.recordSuccess(startTime);
                request.getServiceEndpoint().requestComplete(true);
                return retval;
            } else {
                if (span != null) {
                    Tags.HTTP_STATUS.set(span, lastStatusCode);
                    Tags.ERROR.set(span, true);
                    span.finish();
                }
                methodTimer.recordFailure(startTime);
                //4xx errors should not change circuit-breaker state
                request.getServiceEndpoint().requestComplete(lastStatusCode < 500);

                if (lastStatusCode != RpcCallException.Category.RequestTimedOut.getHttpStatus()) {
                    lastException = decoder.decodeException(retval);
                    if (lastException != null && !lastException.isRetriable()) {
                        throw lastException;
                    }
                }
                if (tryCount < client.getRetries()) {
                    waitForRetryIfNecessary(retryTimeoutDuration);
                    request = createHttpPost(request, triedEndpoints);
                }
            }
            tryCount++;
        } while (request != null && tryCount <= client.getRetries());

        if (lastException == null) {
            throw new RpcCallException(RpcCallException.Category.fromStatus(lastStatusCode),
                    "Null response in execute").withSource(serviceProps.getServiceName());
        } else {
            throw lastException;
        }
    }

    private void waitForRetryIfNecessary(final Duration retryTimeoutDuration) {
        if (retryTimeoutDurationApplicable(retryTimeoutDuration)) {
            Long time = new Date().getTime();
            while ((new Date().getTime() - time) <= retryTimeoutDuration.get(ChronoUnit.MILLIS)) {
                // just wait
                try {
                    wait(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean retryTimeoutDurationApplicable(final Duration retryTimeoutDuration) {
        return retryTimeoutDuration != null && !retryTimeoutDuration.isNegative();
    }

    private boolean responseWasSuccessful(RpcCallExceptionDecoder decoder,
                                          ContentResponse response, int lastStatusCode) throws RpcCallException {
        if (shouldExposeErrorsToHttp(serviceProps)) {
            return lastStatusCode == 200 && response != null && response.getContent().length > 0;
        } else if (lastStatusCode != 0 && lastStatusCode != 200) {
            return false;
        } else if (response == null || response.getContent() == null) {
            return false;
        }
        RpcCallException exception = decoder.decodeException(response);
        return (exception == null);
    }

    private GoTimer getMethodTimer() {
        if (rpcClientMetrics == null) {
            return new GoTimer("");
        } else {
            return rpcClientMetrics.getMethodTimer(client.getServiceName(),
                    client.getMethodName());
        }
    }

    private Marker getRemoteMethod() {
        return append("method", client.getServiceMethodName());
    }

}
