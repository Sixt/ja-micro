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
import com.google.inject.Injector;
import com.google.protobuf.Message;
import com.sixt.service.framework.ServiceProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds RpcClients to interact with remote services.
 */
public class RpcClientBuilder<RESPONSE extends Message> {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientBuilder.class);

    public final static int DEFAULT_RETRIES = 1;
    public final static int DEFAULT_RESPONSE_TIMEOUT = 1000;

    private final Injector injector;
    private String serviceName;
    private String methodName;
    private int retries;
    private RetryBackOffFunction retryBackOffFunction;
    private int timeout;
    private Class<RESPONSE> responseClass;

    @Inject
    public RpcClientBuilder(Injector injector) {
        this.injector = injector;
        initialize();
    }

    void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * Modify retry policy from default
     */
    public RpcClientBuilder<RESPONSE> withRetries(int retries) {
        this.retries = retries;
        return this;
    }

    /**
     * Modify timeout policy from default
     * @param timeout milliseconds
     */
    public RpcClientBuilder<RESPONSE> withTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * RPC call retry timeout function implementation
     *
     * @param retryBackOffFunction - implementation of final RpcClient.RetryBackOffFunction
     */
    public RpcClientBuilder<RESPONSE> withRetryBackOff(final RetryBackOffFunction retryBackOffFunction) {
        this.retryBackOffFunction = retryBackOffFunction;
        return this;
    }

    public RpcClient<RESPONSE> build() {
        if (StringUtils.isBlank(serviceName)) {
            throw new IllegalStateException("RpcClientBuilder: Service name was not set");
        }
        if (StringUtils.isBlank(methodName)) {
            throw new IllegalStateException("RpcClientBuilder: Method name was not set");
        }
        LoadBalancerFactory lbFactory = injector.getInstance(LoadBalancerFactory.class);
        LoadBalancer loadBalancer = lbFactory.getLoadBalancer(serviceName);
        return new RpcClient<>(loadBalancer, serviceName, methodName, retries, timeout,
                               retryBackOffFunction, responseClass);
    }

    public void setResponseClass(Class<RESPONSE> responseClass) {
        this.responseClass = responseClass;
    }

    private void initialize() {
        ServiceProperties properties = injector.getInstance(ServiceProperties.class);
        retries = parseSetting(properties, "rpcClientRetries", DEFAULT_RETRIES);
        timeout = parseSetting(properties, "rpcClientTimeout", DEFAULT_RESPONSE_TIMEOUT);
    }

    private int parseSetting(ServiceProperties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (StringUtils.isNotBlank(value)) {
            try {
                int retval = Integer.parseInt(value);
                if (retval < 0) {
                    throw new IllegalArgumentException("Invalid " + key + " setting: " + value);
                }
                return retval;
            } catch (Exception ex) {
                logger.warn("Caught exception", ex);
            }
        }
        return defaultValue;
    }

}
