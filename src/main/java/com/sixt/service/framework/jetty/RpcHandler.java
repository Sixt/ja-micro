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

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.Message;
import com.sixt.service.framework.*;
import com.sixt.service.framework.metrics.GoTimer;
import com.sixt.service.framework.rpc.RpcCallException;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static com.google.common.collect.ImmutableSortedSet.of;

public abstract class RpcHandler {

    private static final Logger logger = LoggerFactory.getLogger(RpcHandler.class);

    protected final MethodHandlerDictionary handlers;
    protected final MetricRegistry metricRegistry;
    protected final RpcHandlerMetrics handlerMetrics;
    protected final ServiceProperties serviceProps;
    protected final Tracer tracer;

    //For now, we block services from getting certain input headers.
    //The reason is that these headers are also then used for outgoing requests.
    //If you need the incoming headers, we can create an additional bucket inside of OrangeContext to hold them.
    private static final Set<String> blackListedHeaders = of("user-agent", "content-length", "content-type",
            "date", "expect", "host", "micro-service", "micro-endpoint", "micro-method",
            "x-micro-service", "x-micro-endpoint", "x-micro-method");

    public RpcHandler(MethodHandlerDictionary handlers, MetricRegistry registry,
                      RpcHandlerMetrics handlerMetrics, ServiceProperties serviceProperties,
                      Tracer tracer) {
        this.handlers = handlers;
        this.metricRegistry = registry;
        this.handlerMetrics = handlerMetrics;
        this.serviceProps = serviceProperties;
        this.tracer = tracer;
    }

    protected void incrementFailureCounter(String methodName, String originService,
                                           String originMethod) {
        handlerMetrics.incrementFailureCounter(methodName, originService, originMethod);
    }

    protected void incrementSuccessCounter(String methodName, String originService,
                                           String originMethod) {
        handlerMetrics.incrementSuccessCounter(methodName, originService, originMethod);
    }

    protected GoTimer getMethodTimer(String methodName, String originService,
                                     String originMethod) {
        return handlerMetrics.getMethodTimer(methodName, originService, originMethod);
    }

    protected Span getSpan(String methodName, Map<String, String> headers, OrangeContext context) {
        Span span = null;
        if (tracer != null) {
            SpanContext spanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headers));
            if (spanContext != null) {
                span = tracer.buildSpan(methodName).asChildOf(spanContext).start();
            } else {
                span = tracer.buildSpan(methodName).start();
            }
            span.setTag("correlation_id", context.getCorrelationId());
            span.setTag("rpc.call", methodName);
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
            Tags.PEER_SERVICE.set(span, context.getRpcOriginService());
            context.setTracingContext(span.context());
        }
        return span;
    }

    protected Map<String, String> gatherHttpHeaders(HttpServletRequest req) {
        Map<String, String> headers = new HashMap<>();

        Enumeration<String> headerNames = req.getHeaderNames();
        if (headerNames == null) {
            return headers;
        }

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = req.getHeaders(headerName);
            if (headerValues == null) {
                continue;
            }

            String headerNameLower = headerName.toLowerCase();
            if (! blackListedHeaders.contains(headerNameLower)) {
                if (headerValues.hasMoreElements()) {
                    headers.put(headerNameLower, headerValues.nextElement());
                }

                while (headerValues.hasMoreElements()) {
                    logger.debug("Duplicate http-header, discarding: {} = {}", headerName, headerValues.nextElement());
                }
            } else {
                logger.trace("Blocking header {}", headerNameLower);
            }
        }

        return headers;
    }

    /**
     * Invoke in the following order:
     * <ol><li>Global pre-handler hooks</li>
     * <li>Pre-handler hooks for this methodName</li>
     * <li>The handler</li>
     * <li>Post-handler hooks for this methodName</li>
     * <li>Global post-handler hooks</li></ol>
     */
    @SuppressWarnings("unchecked")
    protected Message invokeHandlerChain(String methodName, ServiceMethodHandler handler,
                                         Message request, OrangeContext context) throws RpcCallException {
        List<ServiceMethodPreHook<? extends Message>> preHooks = handlers.getPreHooksFor(methodName);
        for (ServiceMethodPreHook hook : preHooks) {
            request = hook.handleRequest(request, context);
        }
        Message response = handler.handleRequest(request, context);
        List<ServiceMethodPostHook<? extends Message>> postHooks = handlers.getPostHooksFor(methodName);
        for (ServiceMethodPostHook hook : postHooks) {
            response = hook.handleRequest(response, context);
        }
        return response;
    }

    protected void writeResponse(HttpServletResponse resp, int statusCode, String s) throws IOException {
        if (statusCode != 200 && FeatureFlags.shouldExposeErrorsToHttp(serviceProps)) {
            resp.setStatus(statusCode);
        } else {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
        resp.getWriter().write(s);
        resp.getWriter().flush();
    }

}
