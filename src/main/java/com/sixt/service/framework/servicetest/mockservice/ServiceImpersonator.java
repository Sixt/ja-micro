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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.Message;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.health.HealthCheckManager;
import com.sixt.service.framework.injection.ServiceRegistryModule;
import com.sixt.service.framework.injection.TracingModule;
import com.sixt.service.framework.kafka.KafkaPublisher;
import com.sixt.service.framework.kafka.KafkaPublisherFactory;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.registry.ServiceDiscoveryProvider;
import com.sixt.service.framework.registry.consul.RegistrationManager;
import com.sixt.service.framework.rpc.LoadBalancerFactory;
import com.sixt.service.framework.rpc.RpcClientFactory;
import com.sixt.service.framework.servicetest.injection.TestInjectionModule;
import com.sixt.service.framework.util.Sleeper;
import com.squareup.wire.schema.internal.parser.RpcMethodDefinition;
import com.squareup.wire.schema.internal.parser.RpcMethodScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unchecked")
public class ServiceImpersonator {

    private static final Logger logger = LoggerFactory.getLogger(ServiceImpersonator.class);

    public static final int SLEEP_AFTER_PUBLISH = 500;

    protected String serviceName;
    protected RegistrationManager registrationManager;
    protected MessageHandler messageHandler;
    protected MethodHandlerDictionary methodHandlers;
    protected ServiceProperties serviceProperties;
    protected HealthCheckManager healthCheckManager;
    protected Injector injector;
    protected RpcMethodScanner rpcMethodScanner;
    protected KafkaPublisherFactory factory;
    protected LoadBalancerFactory loadBalancerFactory;
    private Map<String, KafkaPublisher> topicToPublisher = new HashMap<>();
    private int sleepAfterPublish = SLEEP_AFTER_PUBLISH;

    public ServiceImpersonator(String serviceName, ServiceProperties props) throws Exception {
        //ServiceImpersonator needs its own injection stack so that each mock service
        //and service under servicetest get their own ecosystem
        this.serviceName = serviceName;
        TestInjectionModule testInjectionModule = new TestInjectionModule(serviceName, props);
        serviceProperties = testInjectionModule.getServiceProperties();
        serviceProperties.setServiceName(serviceName); //has to be before getting regMgr
        serviceProperties.setServiceInstanceId(UUID.randomUUID().toString());
        serviceProperties.addProperty("registry", "consul");
        injector = Guice.createInjector(testInjectionModule, new ServiceRegistryModule(serviceProperties),
                new TracingModule(serviceProperties));
        ServiceDiscoveryProvider provider = injector.getInstance(ServiceDiscoveryProvider.class);
        LoadBalancerFactory lbFactory = injector.getInstance(LoadBalancerFactory.class);
        lbFactory.initialize(provider);
        registrationManager = injector.getInstance(RegistrationManager.class);
        healthCheckManager = injector.getInstance(HealthCheckManager.class);
        rpcMethodScanner = new RpcMethodScanner(injector.getInstance(RpcClientFactory.class));
        methodHandlers = injector.getInstance(MethodHandlerDictionary.class);
        messageHandler = injector.getInstance(MessageHandler.class);
        factory = injector.getInstance(KafkaPublisherFactory.class);
        initialize();
    }

    public ServiceImpersonator(String serviceName) throws Exception {
        this(serviceName, new ServiceProperties());
    }

    public void setSleepAfterPublish(int sleepAfterPublish) {
        this.sleepAfterPublish = sleepAfterPublish;
    }

    private void initialize() throws Exception {
        buildMethodHandlers(serviceName.replaceAll("-", "_"));
        registrationManager.setRegisteredHandlers(methodHandlers.getMethodHandlers());
        registrationManager.register();
        messageHandler.setServiceName(serviceName);
        messageHandler.start();

        while(! registrationManager.isRegistered()) {
            logger.info("Waiting for service registration of {}", serviceName);
            new Sleeper().sleepNoException(100);
        }
        healthCheckManager.initialize();

        loadBalancerFactory = injector.getInstance(LoadBalancerFactory.class);
        loadBalancerFactory.getLoadBalancer(serviceName).waitForServiceInstance();
    }

    public void shutdown() {
        healthCheckManager.shutdown();
        loadBalancerFactory.shutdown();
        registrationManager.shutdown();
        messageHandler.shutdown();
    }

    public ServiceImpersonator addMapping(CommandResponseMapping mapping) {
        ServiceMethodProxy proxy = ((ServiceMethodProxy) this.methodHandlers
                .getMethodHandler(mapping.getCommand()));

        if (proxy == null) {
            throw new RuntimeException("The method " + mapping.getCommand() + " for " +
                    this.serviceName + " could not be found");
        }
        logger.info("Adding mock response mapping for {}", mapping.getCommand());

        proxy.setResponse(mapping.getResponse());
        proxy.setException(mapping.getException());

        return this;
    }

    public Class<? extends Message> getResponseClassForMethod(String method) {
        ServiceMethodProxy proxy = ((ServiceMethodProxy) this.methodHandlers
                .getMethodHandler(method));
        if (proxy == null) {
            throw new RuntimeException("The method " + method + " for " +
                    this.serviceName + " could not be found");
        }
        return proxy.getResponseType();
    }

    /**
     * Publish an event to kafka. We create one publisher per topic.  Uses a null key.
     *
     * @param topic The topic to publish under
     * @param event The event to publish
     */
    public void publishEvent(String topic, Message event) {
        publishEventWithKey(topic, null, event);
    }

    /**
     * Publish an event to kafka. We create one publisher per topic.
     *
     * @param topic The topic to publish under
     * @param key   The key for the event
     * @param event The event to publish
     */
    public void publishEventWithKey(String topic, String key, Message event) {

        KafkaPublisher publisher = topicToPublisher.get(topic);

        if (publisher == null) {
            publisher = factory.newBuilder(topic).build();
            topicToPublisher.put(topic, publisher);
        }

        String jsonEvent = ProtobufUtil.protobufToJson(event).toString();
        boolean isPublished = publisher.publishSyncWithKey(key, jsonEvent);

        if (isPublished){
            logger.info("Published event: {}", jsonEvent);
            new Sleeper().sleepNoException(sleepAfterPublish);
        } else{
            logger.warn("Publishing event message {} to Kafka topic {} failed", jsonEvent, topic);
        }

    }

    /**
     * Get counter how often a service method was called
     *
     * @param serviceMethodName The service method name e.g. Telematics.LockVehicle
     * @return Number of calls of a service method
     */
    public int getServiceMethodCallCount(String serviceMethodName) throws Exception {
        ServiceMethodProxy serviceMethodProxy = (ServiceMethodProxy) methodHandlers.getMethodHandler(serviceMethodName);
        if(serviceMethodProxy == null){
            throw new Exception("No method handler found for service method '" + serviceMethodName + "'");
        }

        return serviceMethodProxy.getMethodCallCounter();
    }

    /**
     * Reset counter how often a service method was called to 0
     *
     * @param serviceMethodName The service method name e.g. Telematics.LockVehicle
     */
    public void resetServiceMethodCallCount(String serviceMethodName) throws Exception {
        ServiceMethodProxy serviceMethodProxy = (ServiceMethodProxy) methodHandlers.getMethodHandler(serviceMethodName);
        if(serviceMethodProxy == null){
            throw new Exception("No method handler found for service method '" + serviceMethodName + "'");
        }

        serviceMethodProxy.resetMethodCallCounter();
    }

    private void buildMethodHandlers(String serviceName) throws Exception {
        // get the generated proto classes
        List<String> protoClasses = rpcMethodScanner.getGeneratedProtoClasses(serviceName);
        List<RpcMethodDefinition> defs = rpcMethodScanner.getRpcMethodDefinitions(serviceName);

        for (RpcMethodDefinition def : defs) {
            ServiceMethodProxy handler = buildMethodHandler(def, protoClasses);
            methodHandlers.put(def.getMethodName(), handler);
            logger.info("Adding mock method handler for {}", def.getMethodName());
        }
    }

    private ServiceMethodProxy buildMethodHandler(RpcMethodDefinition def, List<String> protoClasses)
            throws ClassNotFoundException {
        Class<?> requestClass = rpcMethodScanner.findProtobufClass(protoClasses, def.getRequestType());
        Class<?> responseClass = rpcMethodScanner.findProtobufClass(protoClasses, def.getResponseType());
        return new ServiceMethodProxy(requestClass, responseClass);
    }
}
