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
import com.google.common.io.CharStreams;
import com.google.gson.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Message;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.json.JsonRpcRequest;
import com.sixt.service.framework.json.JsonRpcResponse;
import com.sixt.service.framework.metrics.GoTimer;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.rpc.RpcCallException;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static com.sixt.service.framework.OrangeContext.CORRELATION_ID;
import static com.sixt.service.framework.jetty.RpcServlet.TYPE_JSON;
import static com.sixt.service.framework.json.JsonRpcRequest.METHOD_FIELD;
import static com.sixt.service.framework.json.JsonRpcRequest.PARAMS_FIELD;
import static com.sixt.service.framework.json.JsonRpcResponse.ERROR_FIELD;
import static com.sixt.service.framework.util.ReflectionUtil.findSubClassParameterType;

@Singleton
public class JsonHandler extends RpcHandler {

    private static final Logger logger = LoggerFactory.getLogger(JsonHandler.class);

    @Inject
    public JsonHandler(MethodHandlerDictionary handlers, MetricRegistry registry,
                       RpcHandlerMetrics handlerMetrics, ServiceProperties serviceProperties, Tracer tracer) {
        super(handlers, registry, handlerMetrics, serviceProperties, tracer);
    }

    public void doPost(HttpServletRequest req, HttpServletResponse resp) {
        logger.debug("Handling json request");

        GoTimer methodTimer = null;
        String methodName = null;
        Span span = null;
        long startTime = System.nanoTime();
        Map<String, String> headers = gatherHttpHeaders(req);
        OrangeContext context = new OrangeContext(headers);
        try {

            MDC.put(CORRELATION_ID, context.getCorrelationId());

            String postedContent = CharStreams.toString(req.getReader());
            logger.debug("Request JSON: {}", postedContent);

            JsonRpcRequest rpcRequest;

            try {
                rpcRequest = parseRpcRequest(postedContent);
            } catch (IllegalArgumentException iaex) {
                logger.warn("Error parsing request: " + postedContent, iaex);
                @SuppressWarnings("ThrowableNotThrown")
                RpcCallException callException = new RpcCallException(RpcCallException.Category.BadRequest,
                        iaex.getMessage());
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.add(ERROR_FIELD, callException.toJson());
                writeResponse(resp, HttpServletResponse.SC_BAD_REQUEST, jsonResponse.toString());
                incrementFailureCounter("unknown", context.getRpcOriginService(),
                        context.getRpcOriginMethod());
                return;
            }

            methodName = rpcRequest.getMethod();

            span = getSpan(methodName, headers, context);

            methodTimer = getMethodTimer(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
            startTime = methodTimer.start();
            context.setCorrelationId(rpcRequest.getIdAsString());
            JsonRpcResponse finalResponse = dispatchJsonRpcRequest(rpcRequest, context);

            resp.setContentType(TYPE_JSON);
            writeResponse(resp, finalResponse.getStatusCode(), finalResponse.toJson().toString());

            //TODO: should we check the response for errors (for metrics)?
            methodTimer.recordSuccess(startTime);
            incrementSuccessCounter(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
        } catch (IOException e) {
            if (span != null) {
                Tags.ERROR.set(span, true);
            }
            //TODO: this case doesn't return a response.  should it?
            methodTimer.recordFailure(startTime);
            logger.error("Error handling request", e);
            incrementFailureCounter(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
        } finally {
            if (span != null) {
                span.finish();
            }
            MDC.remove(CORRELATION_ID);
        }
    }

    protected JsonRpcRequest parseRpcRequest(String jsonRequestString)
            throws IllegalArgumentException {
        JsonObject jsonRpcRequest = null;
        try {
            jsonRpcRequest = new JsonParser().parse(jsonRequestString).getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
        JsonElement methodElement = jsonRpcRequest.get(METHOD_FIELD);
        if (methodElement == null || methodElement.getAsString().isEmpty()) {
            throw new IllegalArgumentException("Missing method name");
        }
        if (! handlers.hasMethodHandler(methodElement.getAsString())) {
            throw new IllegalArgumentException("No handler registered for method '" +
                    methodElement.getAsString() + "'");
        }

        JsonArray paramsArray = jsonRpcRequest.getAsJsonArray(PARAMS_FIELD);
        JsonElement idElement = jsonRpcRequest.get(JsonRpcRequest.ID_FIELD);
        return new JsonRpcRequest(idElement, methodElement.getAsString(), paramsArray);
    }

    @SuppressWarnings("unchecked")
    private JsonRpcResponse dispatchJsonRpcRequest(JsonRpcRequest rpcRequest, OrangeContext cxt) {
        JsonRpcResponse jsonResponse = new JsonRpcResponse(rpcRequest.getId(), JsonNull.INSTANCE,
                JsonNull.INSTANCE, HttpServletResponse.SC_OK);
        try {
            ServiceMethodHandler handler = handlers.getMethodHandler(rpcRequest.getMethod());
            Message innerRequest = convertJsonToProtobuf(handler, rpcRequest);
            JsonElement idElement = rpcRequest.getId();
            if (idElement == null) {
                jsonResponse.setId(new JsonPrimitive(-1));
            }
            Message innerResponse = invokeHandlerChain(rpcRequest.getMethod(), handler, innerRequest, cxt);
            jsonResponse.setResult(ProtobufUtil.protobufToJson(innerResponse));
        } catch (RpcCallException rpcEx) {
            logger.debug("Error processing request", rpcEx);
            jsonResponse.setError(rpcEx.toJson());
            jsonResponse.setStatusCode(rpcEx.getCategory().getHttpStatus());
        } catch (Exception ex) {
            logger.warn("Error processing request", ex);
            if (ex.getMessage() != null) {
                jsonResponse.setError(new JsonPrimitive(ex.getMessage()));
            }
            jsonResponse.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return jsonResponse;
    }

    @SuppressWarnings("unchecked")
    private Message convertJsonToProtobuf(ServiceMethodHandler handler,
                                          JsonRpcRequest rpcRequest) throws RpcCallException {
        try {
            Class<?> requestKlass = findSubClassParameterType(handler, 0);
            return ProtobufUtil.jsonToProtobuf(rpcRequest.getParams(),
                    (Class<? extends Message>) requestKlass);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Reflection for handler " +
                    handler.getClass() + " failed");
        } catch (RuntimeException ex) {
            throw new RpcCallException(RpcCallException.Category.BadRequest, "Invalid request");
        }
    }

}
