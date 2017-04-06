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
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Message;
import com.sixt.service.framework.*;
import com.sixt.service.framework.metrics.GoTimer;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.protobuf.RpcEnvelope;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.util.ReflectionUtil;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Singleton
public class ProtobufHandler extends RpcHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProtobufHandler.class);

    @Inject
    public ProtobufHandler(MethodHandlerDictionary handlers, MetricRegistry registry,
                           RpcHandlerMetrics handlerMetrics, ServiceProperties serviceProperties, Tracer tracer) {
        super(handlers, registry, handlerMetrics, serviceProperties, tracer);
    }

    @SuppressWarnings("unchecked")
    public void doPost(HttpServletRequest req, HttpServletResponse resp) {
        logger.debug("Handling protobuf request");

        RpcEnvelope.Request rpcRequest = null;
        String methodName = null;
        Span span = null;
        Map<String, String> headers = gatherHttpHeaders(req);
        OrangeContext context = new OrangeContext(headers);
        HttpServletRequest blubb = new HttpServletRequestWrapper(req);

        try {
            MDC.put(OrangeContext.CORRELATION_ID, context.getCorrelationId());

            ServletInputStream in = req.getInputStream();
            rpcRequest = readRpcEnvelope(in);

            methodName = rpcRequest.getServiceMethod();

            span = getSpan(methodName, headers, context);

            ServiceMethodHandler handler = handlers.getMethodHandler(methodName);
            if (handler == null) {
                incrementFailureCounter(methodName, context.getRpcOriginService(),
                        context.getRpcOriginMethod());
                throw new IllegalArgumentException("Invalid method: " +
                        rpcRequest.getServiceMethod());
            }

            Class<? extends Message> requestClass = (Class<? extends Message>)
                    ReflectionUtil.findSubClassParameterType(handler, 0);

            Message pbRequest = readRpcBody(in, requestClass);

            GoTimer methodTimer = getMethodTimer(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
            long startTime = methodTimer.start();

            Message pbResponse = invokeHandlerChain(methodName, handler, pbRequest, context);

            resp.setContentType(RpcServlet.TYPE_OCTET);
            sendSuccessfulResponse(resp, rpcRequest, pbResponse);

            //TODO: should we check the response for errors?
            methodTimer.recordSuccess(startTime);
            incrementSuccessCounter(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
        } catch (RpcCallException rpcEx) {
            sendErrorResponse(resp, rpcRequest, rpcEx.toString(), rpcEx.getCategory().getHttpStatus());
            if (span != null) {
                Tags.ERROR.set(span, true);
            }
            incrementFailureCounter(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
        } catch (RpcReadException ex) {
            logger.warn("bad request ( cannot decode rpc message )", ex.toJSON(req));
            sendErrorResponse(resp, rpcRequest, ex.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
            if (span != null) {
                Tags.ERROR.set(span, true);
            }
            incrementFailureCounter(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
        } catch (Exception ex) {
            logger.warn("Uncaught exception", ex);
            sendErrorResponse(resp, rpcRequest, ex.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (span != null) {
                Tags.ERROR.set(span, true);
            }
            incrementFailureCounter(methodName, context.getRpcOriginService(),
                    context.getRpcOriginMethod());
        } finally {
            if (span != null) {
                span.finish();
            }
            MDC.remove(OrangeContext.CORRELATION_ID);
        }
    }

    private void sendSuccessfulResponse(HttpServletResponse response,
                                        RpcEnvelope.Request rpcRequest,
                                        Message pbResponse) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);

        RpcEnvelope.Response rpcResponse = RpcEnvelope.Response.newBuilder().
                setServiceMethod(rpcRequest.getServiceMethod()).
                setSequenceNumber(rpcRequest.getSequenceNumber()).build();
        byte responseHeader[] = rpcResponse.toByteArray();
        byte responseBody[];
        if (pbResponse == null) {
            responseBody = new byte[0];
        } else {
            responseBody = pbResponse.toByteArray();
        }

        ServletOutputStream out = response.getOutputStream();

        out.write(Ints.toByteArray(responseHeader.length));
        out.write(responseHeader);

        out.write(Ints.toByteArray(responseBody.length));
        out.write(responseBody);
    }

    private void sendErrorResponse(HttpServletResponse resp,
                                   RpcEnvelope.Request rpcRequest,
                                   String message,
                                   int httpStatusCode) {
        if (rpcRequest != null) {
            try {
                if (FeatureFlags.shouldExposeErrorsToHttp(serviceProps)) {
                    resp.setStatus(httpStatusCode);
                } else {
                    resp.setStatus(HttpServletResponse.SC_OK);
                }
                if (message == null) {
                    message = "null";
                }
                RpcEnvelope.Response rpcResponse = RpcEnvelope.Response.newBuilder().
                        setServiceMethod(rpcRequest.getServiceMethod()).
                        setSequenceNumber(rpcRequest.getSequenceNumber()).
                        setError(message).build();
                byte responseHeader[] = rpcResponse.toByteArray();
                ServletOutputStream out = resp.getOutputStream();
                out.write(Ints.toByteArray(responseHeader.length));
                out.write(responseHeader);
                out.write(Ints.toByteArray(0)); //zero-length (no) body
            } catch (Exception ex) {
                logger.warn("Error writing error response", ex);
            }
        }
    }

    private RpcEnvelope.Request readRpcEnvelope(ServletInputStream in) throws Exception {
        byte chunkSize[] = new byte[4];
        in.read(chunkSize);
        int size = Ints.fromByteArray(chunkSize);
        if (size <= 0 || size > ProtobufUtil.MAX_HEADER_CHUNK_SIZE) {
            String message = "Invalid header chunk size: " + size;
            throw new RpcReadException(chunkSize, in, message);
        }
        byte headerData[] = readyFully(in, size);
        RpcEnvelope.Request rpcRequest = RpcEnvelope.Request.parseFrom(headerData);
        return rpcRequest;
    }

    private Message readRpcBody(ServletInputStream in,
                                Class<? extends Message> requestClass) throws Exception {
        byte chunkSize[] = new byte[4];
        in.read(chunkSize);
        int size = Ints.fromByteArray(chunkSize);
        if (size == 0) {
            return ProtobufUtil.newEmptyMessage(requestClass);
        }
        if (size > ProtobufUtil.MAX_BODY_CHUNK_SIZE) {
            String message = "Invalid body chunk size: " + size;
            throw new RpcReadException(chunkSize, in, message);
        }
        byte bodyData[] = readyFully(in, size);
        Message pbRequest = ProtobufUtil.byteArrayToProtobuf(bodyData, requestClass);
        return pbRequest;
    }

    private byte[] readyFully(ServletInputStream in, int totalSize) throws Exception {
        byte[] retval = new byte[totalSize];
        int bytesRead = 0;
        while (bytesRead < totalSize) {
            try {
                int read = in.read(retval, bytesRead, totalSize - bytesRead);
                if (read == -1) {
                    throw new RpcCallException(RpcCallException.Category.InternalServerError,
                            "Unable to read complete request or response");
                }
                bytesRead += read;
            } catch (IOException e) {
                throw new RpcCallException(RpcCallException.Category.InternalServerError,
                        "IOException reading data: " + e);
            }
        }
        return retval;
    }

}
