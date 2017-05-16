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

import com.google.gson.JsonArray;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.json.JsonRpcRequest;
import com.sixt.service.framework.json.JsonRpcResponse;
import com.sixt.service.framework.protobuf.ProtobufRpcRequest;
import com.sixt.service.framework.protobuf.ProtobufRpcResponse;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.sixt.service.framework.jetty.RpcServlet.TYPE_JSON;
import static com.sixt.service.framework.jetty.RpcServlet.TYPE_OCTET;

/**
 * Interface to call a method on a remote service
 * TODO: add asynchronous call support
 * To make multiple simultaneous calls to multiple services, utilize async calls and
 * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html (allOf)
 */
public class RpcClient<RESPONSE extends Message> {

    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);

    private final LoadBalancer loadBalancer;
    private final String serviceName;
    private final String methodName;
    private Class<RESPONSE> responseClass;
    private int retries;
    private int timeout;

    @Inject
    public RpcClient(LoadBalancer loadBalancer, String serviceName, String methodName,
                     int retries, int timeout, Class<RESPONSE> responseClass) {
        this.loadBalancer = loadBalancer;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.retries = retries;
        this.timeout = timeout;
        this.responseClass = responseClass;
    }

    /**
     * @deprecated use {@link #callSynchronous(JsonArray, OrangeContext)} instead and make sure to always pass the {@link OrangeContext}
     */
    @Deprecated
    public String callSynchronous(JsonArray params)
            throws RpcCallException {
        return callSynchronous(params, null);
    }

    public String callSynchronous(JsonArray params, OrangeContext orangeContext)
            throws RpcCallException {
        HttpClientWrapper clientWrapper = loadBalancer.getHttpClientWrapper();
        HttpRequestWrapper balancedPost = clientWrapper.createHttpPost(this);

        //set custom headers
        if (orangeContext != null) {
            orangeContext.getProperties().forEach(balancedPost::setHeader);
        }

        balancedPost.setHeader("Content-type", TYPE_JSON);
        //TODO: fix: Temporary workaround below until go services are more http compliant
        balancedPost.setHeader("Connection", "close");
        JsonRpcRequest jsonRequest = new JsonRpcRequest(null, methodName, params);
        String json = jsonRequest.toString();
        balancedPost.setContentProvider(new StringContentProvider(json));

        logger.debug("Sending request of size {}", json.length());
        ContentResponse rpcResponse = clientWrapper.execute(balancedPost,
                new JsonRpcCallExceptionDecoder(), orangeContext);
        String rawResponse = rpcResponse.getContentAsString();
        logger.debug("Json response from the service: {}", rawResponse);

        return JsonRpcResponse.fromString(rawResponse).getResult().getAsString();
    }

    /**
     * @deprecated use {@link #callSynchronous(Message, OrangeContext)} instead and make sure to always pass the {@link OrangeContext}
     */
    @Deprecated
    public RESPONSE callSynchronous(Message request) throws RpcCallException {
        return callSynchronous(request, null);
    }

    public RESPONSE callSynchronous(Message request, OrangeContext orangeContext) throws RpcCallException {
        HttpClientWrapper clientWrapper = loadBalancer.getHttpClientWrapper();
        HttpRequestWrapper balancedPost = clientWrapper.createHttpPost(this);

        //set custom headers
        if (orangeContext != null) {
            orangeContext.getProperties().forEach(balancedPost::setHeader);
        }

        balancedPost.setHeader("Content-type", TYPE_OCTET);
        //TODO: fix: Temporary workaround below until go services are more http compliant
        balancedPost.setHeader("Connection", "close");
        ProtobufRpcRequest pbRequest = new ProtobufRpcRequest(methodName, request);
        byte[] protobufData = pbRequest.getProtobufData();
        balancedPost.setContentProvider(new BytesContentProvider(protobufData));

        logger.debug("Sending request of size {}", protobufData.length);
        ContentResponse rpcResponse = clientWrapper.execute(balancedPost,
                new ProtobufRpcCallExceptionDecoder(), orangeContext);
        byte[] data = rpcResponse.getContent();
        logger.debug("Received a proto response of size: {}", data.length);

        return ProtobufUtil.byteArrayToProtobuf(
                new ProtobufRpcResponse(data).getPayloadData(), responseClass);
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getServiceMethodName() {
        return serviceName + "." + methodName;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
