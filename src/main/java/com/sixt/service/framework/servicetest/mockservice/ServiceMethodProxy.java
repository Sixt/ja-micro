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

package com.sixt.service.framework.servicetest.mockservice;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.util.MockMethodHandler;

import com.google.protobuf.Message;

import java.util.concurrent.atomic.AtomicInteger;

//TODO: implement publishing kafka events
public class ServiceMethodProxy <REQ extends Message, RES extends Message>
        implements MockMethodHandler<REQ, RES>, ServiceMethodHandler<REQ, RES> {

    protected Class<REQ> requestType;
    protected Class<RES> responseType;
    protected REQ request;
    protected RES response;
    protected RpcCallException exception;

    protected AtomicInteger methodCallCounter = new AtomicInteger(0);

    public ServiceMethodProxy(Class<REQ> requestType, Class<RES> responseType) {
        this.requestType = requestType;
        this.responseType = responseType;
    }

    @Override
    public Class<REQ> getRequestType() {
        return requestType;
    }

    @Override
    public Class<RES> getResponseType() {
        return responseType;
    }

    public Message getRequest() {
        return request;
    }

    @Override
    public RES handleRequest(REQ requestMessage, OrangeContext orangeContext)
            throws RpcCallException {
        request = requestMessage;
        methodCallCounter.incrementAndGet();

        if (exception != null) {
            throw exception;
        }
        return response;
    }

    public void setResponse(RES response) {
        this.response = response;
    }

    @Override
    public void resetMethodCallCounter() {
        methodCallCounter.set(0);
    }

    @Override
    public int getMethodCallCounter() {
        return methodCallCounter.get();
    }

    public void setException(RpcCallException exception) {
        this.exception = exception;
    }
}