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
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.LoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Singleton
public class RegistrationMonitor {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationMonitor.class);

    protected ServiceProperties serviceProps;
    protected ExecutorService executorService;
    protected LoadBalancer loadBalancer;
    protected Map<RegistrationMonitorWorker, Future> workers = new HashMap<>();
    protected Injector injector;

    @Inject
    public RegistrationMonitor(Injector injector,
                               ServiceProperties serviceProps,
                               ExecutorService executorService) {
        this.injector = injector;
        this.serviceProps = serviceProps;
        this.executorService = executorService;
    }

    /**
     * For each service monitored, create a thread that will grab the initial
     * info, then run a long-pool loop to get service updates from consul.
     */
    public void monitorService(LoadBalancer lb) {
        this.loadBalancer = lb;

        RegistrationMonitorWorker worker = injector.getInstance(RegistrationMonitorWorker.class);
        worker.setLoadbalancer(loadBalancer);
        worker.setServiceName(lb.getServiceName());
        Future future = executorService.submit(worker);
        workers.put(worker, future);
    }

    public void shutdown() {
        for (RegistrationMonitorWorker worker : workers.keySet()) {
            worker.shutdown();
        }
    }
}


