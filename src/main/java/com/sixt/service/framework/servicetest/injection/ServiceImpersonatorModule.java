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

package com.sixt.service.framework.servicetest.injection;

import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.servicetest.mockservice.ServiceImpersonatorLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

public class ServiceImpersonatorModule extends TestInjectionModule {

    private static final Logger logger = LoggerFactory.getLogger(ServiceImpersonatorModule.class);

    private ServerSocket serverSocket;

    public ServiceImpersonatorModule(String serviceName, ServiceProperties props) {
        super(serviceName, props);
        serverSocket = buildServerSocket();
        serviceProperties.initialize(new String[0]);
        serviceProperties.setServicePort(serverSocket.getLocalPort());
    }

    @Override
    protected void configure() {
        bind(ServiceProperties.class).toInstance(serviceProperties);
        bind(ServerSocket.class).toInstance(serverSocket);
        bind(LoadBalancer.class).to(ServiceImpersonatorLoadBalancer.class);
    }

    private ServerSocket buildServerSocket() {
        return nextServerSocket();
    }

    private ServerSocket nextServerSocket() {
        int port = portDictionary.newInternalPortForImpersonated(serviceProperties.getServiceName());
        try {
            return new ServerSocket(port);
        } catch (IOException e) {
            logger.error("Could not create ServerSocket on port {}", port);
            return null;
        }
    }

}
