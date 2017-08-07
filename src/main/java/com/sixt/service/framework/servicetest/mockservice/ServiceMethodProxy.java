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

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.util.MockMethodHandler;

import java.util.concurrent.atomic.AtomicInteger;

public class ServiceMethodProxy implements MockMethodHandler, ServiceMethodHandler<Message, Message> {

    protected Class<?> requestType;
    protected Class<?> responseType;
    protected Message response;
    protected Message request;
    protected AtomicInteger methodCallCounter = new AtomicInteger(0);

    public ServiceMethodProxy(Class<?> requestType, Class<?> responseType) {
        this.requestType = requestType;
        this.responseType = responseType;
    }

    @Override
    public Class<?> getRequestType() {
        return requestType;
    }

    @Override
    public Class<?> getResponseType() {
        return responseType;
    }

    public Message getRequest() {
        return request;
    }

    @Override
    public Message handleRequest(Message requestMessage, OrangeContext orangeContext) {
        request = (GeneratedMessageV3) requestMessage;
        methodCallCounter.incrementAndGet();

        return response;
    }

    public void setResponse(Message response) {
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
}