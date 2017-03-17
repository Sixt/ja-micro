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

package com.sixt.service.framework.registry.consul;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.annotation.ServiceRegistryPlugin;
import com.sixt.service.framework.registry.ServiceDiscoveryProvider;
import com.sixt.service.framework.registry.ServiceRegistrationProvider;
import com.sixt.service.framework.rpc.LoadBalancer;

/**
 * Sixt implementation of a ServiceRegistryPlugin for Hashicorp's Consul.
 * https://www.consul.io/
 */

@Singleton
@ServiceRegistryPlugin(name = "consul")
public class ConsulServiceRegistryPlugin implements
        ServiceDiscoveryProvider, ServiceRegistrationProvider {

    private final RegistrationMonitor registrationMonitor;
    private final RegistrationManager registrationManager;

    @Inject
    public ConsulServiceRegistryPlugin(RegistrationMonitor registrationMonitor,
                                       RegistrationManager registrationManager) {
        this.registrationMonitor = registrationMonitor;
        this.registrationManager = registrationManager;
    }

    @Override
    public void monitorService(LoadBalancer lb) {
        registrationMonitor.monitorService(lb);
    }

    @Override
    public void initialize(MethodHandlerDictionary methodHandlers) {
        registrationManager.setRegisteredHandlers(methodHandlers.getMethodHandlers());
        registrationManager.register();
    }

    @Override
    public void shutdown() {
        registrationMonitor.shutdown();
    }

}
