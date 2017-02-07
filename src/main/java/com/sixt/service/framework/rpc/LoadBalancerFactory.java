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
import com.google.inject.Singleton;
import com.sixt.service.framework.registry.ServiceDiscoveryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
public class LoadBalancerFactory {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerFactory.class);

    protected Injector injector;
    protected ServiceDiscoveryProvider provider;
    protected Map<String, LoadBalancer> loadBalancers = new HashMap<>();

    @Inject
    public LoadBalancerFactory(Injector injector) {
        this.injector = injector;
    }

    public synchronized void initialize(ServiceDiscoveryProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("ServiceDiscoveryProvider was null");
        }
        this.provider = provider;
    }

    public synchronized LoadBalancer getLoadBalancer(String serviceName) {
        LoadBalancer retval = loadBalancers.get(serviceName);
        if (retval == null) {
            retval = buildLoadBalancer(serviceName);
        }
        return retval;
    }

    private LoadBalancer buildLoadBalancer(String svc) {
        LoadBalancer retval = injector.getInstance(LoadBalancer.class);
        retval.setServiceName(svc);
        loadBalancers.put(svc, retval);
        if (provider == null) {
            logger.warn("No ServiceDiscoveryProvider configured");
        } else {
            provider.monitorService(retval);
        }
        return retval;
    }

    //intended only for debugging
    public Set<String> getServices() {
        return loadBalancers.keySet();
    }

}

