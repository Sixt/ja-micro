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

package com.sixt.service.framework.servicetest.service;

import com.google.protobuf.Message;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.protobuf.ProtobufRpcRequest;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.rpc.RpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ServiceMethod<RESPONSE extends Message> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceMethod.class);

    private final Class<RESPONSE> responseClass;
    private final RpcClient client;

    public ServiceMethod(RpcClient client, Class<RESPONSE> responseClass) {
        this.responseClass = responseClass;
        this.client = client;
    }

    public RESPONSE sendRequest(Message request) throws RpcCallException {
        return sendRequest(request, null);
    }

    public RESPONSE sendRequest(Message request, OrangeContext orangeContext) throws RpcCallException {

        ProtobufRpcRequest pbRequest = new ProtobufRpcRequest(client.getMethodName(), request);

        logger.info("Calling {} with {}", client.getMethodName(), ProtobufUtil.
                protobufToJson(pbRequest.getPayload()));

        client.getLoadBalancer().waitForServiceInstance();
        RESPONSE response = null;

        response = (RESPONSE) client.callSynchronous(request, orangeContext);

        logger.info("Received response: {}", response);

        return response;
    }

    public Class<RESPONSE> getResponseClass() {
        return responseClass;
    }

    public void setTimeout(int timeout) {
        client.setTimeout(timeout);
    }

    public void setRetries(int count) {
        client.setRetries(count);
    }
}
