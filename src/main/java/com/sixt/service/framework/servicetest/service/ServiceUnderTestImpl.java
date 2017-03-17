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

import com.google.gson.JsonObject;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.Message;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.injection.ServiceRegistryModule;
import com.sixt.service.framework.injection.TracingModule;
import com.sixt.service.framework.registry.ServiceDiscoveryProvider;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.LoadBalancerFactory;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.rpc.RpcClientFactory;
import com.sixt.service.framework.servicetest.eventing.ServiceTestEventHandler;
import com.sixt.service.framework.servicetest.injection.TestInjectionModule;
import com.squareup.wire.schema.internal.parser.RpcMethodScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ServiceUnderTestImpl implements ServiceUnderTest {

    private static final Logger logger = LoggerFactory.getLogger(ServiceUnderTestImpl.class);

    private ServiceTestEventHandler eventHandler;
    private Map<String, ServiceMethod<Message>> serviceMethods;
    private HttpCommandProxy httpCommandProxy;
    private LoadBalancerFactory loadBalancerFactory;
    private LoadBalancer loadBalancer;

    public ServiceUnderTestImpl(String serviceName) {
        this(serviceName, false, null);
    }

    public ServiceUnderTestImpl(String serviceName, String kafkaTopic) {
        this(serviceName, true, kafkaTopic);
    }

    @Deprecated //if you need eventing, please use the form that specifies the topic, else the 1-arg ctor
    public ServiceUnderTestImpl(String serviceName, boolean useEventHandler) {
        this(serviceName, useEventHandler, "events");
    }

    private ServiceUnderTestImpl(String serviceName, boolean useEventHandler, String kafkaTopic) {
        TestInjectionModule baseModule = new TestInjectionModule(serviceName);
        ServiceProperties serviceProperties = baseModule.getServiceProperties();
        Injector injector = Guice.createInjector(baseModule, new ServiceRegistryModule(serviceProperties),
                new TracingModule(serviceProperties));

        ServiceDiscoveryProvider provider = injector.getInstance(ServiceDiscoveryProvider.class);
        loadBalancerFactory = injector.getInstance(LoadBalancerFactory.class);
        loadBalancerFactory.initialize(provider);
        loadBalancer = loadBalancerFactory.getLoadBalancer(serviceName);
        loadBalancer.waitForServiceInstance();

        RpcClientFactory rpcClientFactory = injector.getInstance(RpcClientFactory.class);

        RpcMethodScanner rpcMethodScanner = new RpcMethodScanner(rpcClientFactory);
        serviceMethods = rpcMethodScanner.getMethodHandlers(serviceName);

        httpCommandProxy = injector.getInstance(HttpCommandProxy.class);
        httpCommandProxy.setServiceName(serviceName);

        if (useEventHandler) {
            eventHandler = injector.getInstance(ServiceTestEventHandler.class);
            eventHandler.initialize(kafkaTopic);
        }
    }

    @Override
    public Message sendRequest(String serviceMethod, Message request) throws RpcCallException {
        return sendRequest(serviceMethod, request, null);
    }

    @Override
    public Message sendRequest(String serviceMethod, Message request, OrangeContext orangeContext) throws RpcCallException {
        ServiceMethod<Message> method = serviceMethods.get(serviceMethod);
        return method.sendRequest(request, orangeContext);
    }

    @Override
    public String sendHttpGet(String path) throws Exception {
        return httpCommandProxy.sendHttpGet(path);
    }

    @Override
    public String sendHttpPut(String path, String data) throws Exception {
        return httpCommandProxy.sendHttpPut(path, data);
    }

    @Override
    public String sendHttpDelete(String path) throws Exception {
        return httpCommandProxy.sendHttpDelete(path, null);
    }

    @Override
    public String sendHttpDelete(String path, String data) throws Exception {
        return httpCommandProxy.sendHttpDelete(path, data);
    }

    @Override
    public String sendHttpPost(String path, String data) throws Exception {
        return httpCommandProxy.sendHttpPost(path, data);
    }

    @Override
    public Map<String, Message> getExpectedEvents(Map<String, Class> expectedEvents) {

        if (eventHandler == null) {
            logger.warn("Event handler has not been initialized. Create the " +
                    "ServiceUnderTest with true in the constructor.");
            return new HashMap<>();
        }

        return eventHandler.getExpectedEvents(expectedEvents);
    }

    @Override
    public <TYPE extends Message> List<TYPE> getEventsOfType(String eventName, Class<TYPE> eventClass) {

        if (eventHandler == null) {
            logger.warn("Event handler has not been initialized. Create the " +
                    "ServiceUnderTest with true in the constructor.");
            return new ArrayList<>();
        }
        return eventHandler.getEventsOfType(eventName, eventClass);
    }

    @Override
    public List<JsonObject> getAllJsonEvents() {

        if (eventHandler == null) {
            logger.warn("Event handler has not been initialized. Create the " +
                    "ServiceUnderTest with true in the constructor.");
            return new ArrayList<>();
        }

        return eventHandler.getAllJsonEvents();
    }

    @Override
    public <T> T getEvent(String eventName, Class eventClass, Predicate<T> predicate, long timeoutms) {
        return eventHandler.getEvent(eventName, eventClass, predicate, timeoutms);
    }

    @Override
    public void clearReadEvents() {
        eventHandler.clearReadEvents();
    }

    @Override
    public void setServiceMethodTimeout(String serviceMethod, int timeout) {
        ServiceMethod<Message> method = serviceMethods.get(serviceMethod);
        method.setTimeout(timeout);
    }

    @Override
    public void setDefaultRpcClientRetries(int count) {
    }

    @Override
    public void setDefaultRpcClientTimeout(int timeout) {
        for (ServiceMethod method : serviceMethods.values()) {
            method.setTimeout(timeout);
        }
    }

    @Override
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    @Override
    public void shutdown() {
        loadBalancerFactory.shutdown();
    }

}
