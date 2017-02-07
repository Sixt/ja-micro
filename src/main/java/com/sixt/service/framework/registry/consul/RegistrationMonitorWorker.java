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
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.CircuitBreakerState;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.LoadBalancerUpdate;
import com.sixt.service.framework.rpc.ServiceEndpoint;
import com.sixt.service.framework.util.Sleeper;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.logstash.logback.marker.Markers.append;

//Note: we only consider 'Passing' entries to send requests to.
public class RegistrationMonitorWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationMonitorWorker.class);

    public final static String CONSUL_INDEX = "X-Consul-Index";

    protected HttpClient httpClient;
    protected ServiceProperties serviceProps;
    protected String serviceName;
    protected Map<String, ConsulHealthEntry> discoveredServices; //key is id
    protected Sleeper sleeper = new Sleeper();
    protected Semaphore shutdownSemaphore = new Semaphore(0);
    protected String consulIndex;
    protected LoadBalancer loadbalancer;
    protected ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
    protected AtomicBoolean healthEndpointCalled = new AtomicBoolean(false);

    @Inject
    public RegistrationMonitorWorker(HttpClient httpClient,
                                     ServiceProperties serviceProps) {
        this.httpClient = httpClient;
        this.serviceProps = serviceProps;
        this.discoveredServices = new LinkedHashMap<>();
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void run() {
        if (serviceName == null) {
            throw new IllegalStateException("Service name was not set");
        }
        if (StringUtils.isBlank(serviceProps.getRegistryServer())) {
            logger.error("registryServer was not specified");
            return;
        } else {
            logger.info("Will use consul server at {}", serviceProps.getRegistryServer());
        }

        //don't start polling until we are properly initialized, even if the initial result was empty
        List<ConsulHealthEntry> instances = loadCurrentHealthList();
        while (instances.isEmpty()) {
            sleeper.sleepNoException(1000);
            instances = loadCurrentHealthList();
            logger.debug("Received {} health entries for {}", instances.size(), serviceName);
        }

        reportInitialServicesList(instances);

        //TODO: detect if multiple AZs; if multiple AZs, determine AZ sort order

        //TODO: instances = sortByAvailabilityZone(instances);


        while (true) {
            watchForUpdates();
            if (shutdownSemaphore.tryAcquire()) {
                logger.debug("Shutdown semaphore acquired");
                break;
            }
        }

    }

    protected void reportInitialServicesList(List<ConsulHealthEntry> instances) {
        LoadBalancerUpdate update = new LoadBalancerUpdate();
        for (ConsulHealthEntry service : instances) {
            update.addNewService(newServiceEndpoint(service));
            discoveredServices.put(service.getId(), service);
        }
        loadbalancer.updateServiceEndpoints(update);
    }

    protected List<ConsulHealthEntry> loadCurrentHealthList() {

        String requestUrl = getServiceHealthUri();

        Marker logMarker = append("serviceName", serviceName).and(append("requestUrl", requestUrl));

        logger.trace(logMarker,
                "Calling Consul for health list of {}", serviceName);

        ContentResponse httpResponse = null;
        try {
            httpResponse = httpClient.newRequest(requestUrl).send();
        } catch (Exception ex) {
            logger.error(logMarker,
                    "Error calling Consul", ex);
        }

        List<ConsulHealthEntry> healths = new ArrayList<>();
        if (httpResponse.getStatus() == 200) {
            try {
                healths = new ConsulHealthEntryFactory().parse(httpResponse.getContentAsString());
            } catch (IOException ex) {
                logger.error(logMarker,
                        "Could not parse HTTP response from Consul", ex);
            }
            readConsulIndexHeader(httpResponse);
        } else {
            logger.warn(logMarker, "Could not retrieve Consul " +
                    "health information for service {}", serviceName);
        }

        return ConsulHealthEntryFilter.filterHealthyInstances(healths);
    }

    private void readConsulIndexHeader(ContentResponse httpResponse) {
        String value = httpResponse.getHeaders().get(CONSUL_INDEX);
        if (value != null) {
            consulIndex = value;
        }
    }

    protected String getServiceHealthUri() {
        String retval = "http://" + serviceProps.getRegistryServer() + "/v1/health/service/"
                + serviceName + "?stale";
        if (healthEndpointCalled.get() && consulIndex != null) {
            retval += "&index=" + consulIndex;
        } else {
            healthEndpointCalled.set(true);
        }
        return retval;
    }

    protected void watchForUpdates() {
        List<ConsulHealthEntry> instancesHealth = loadCurrentHealthList();

        LoadBalancerUpdate lbUpdate = diffServiceStatus(instancesHealth);

        if (! lbUpdate.isEmpty()) {
            loadbalancer.updateServiceEndpoints(lbUpdate);
        }
    }

    /**
     * Create diff to send to loadbalancer, and also update our baseline.
     */
    protected LoadBalancerUpdate diffServiceStatus(List<ConsulHealthEntry> healthEntries) {
        LoadBalancerUpdate retval = new LoadBalancerUpdate();

        //deletes
        for (String serviceId : discoveredServices.keySet()) {
            boolean found = false;
            for (ConsulHealthEntry health : healthEntries) {
                if (health.getId().equals(serviceId)) {
                    found = true;
                    break;
                }
            }
            if (! found) {
                ConsulHealthEntry service = discoveredServices.get(serviceId);
                if (! service.getStatus().equals(ConsulHealthEntry.Status.Critical)) {
                    service.setStatus(ConsulHealthEntry.Status.Critical);
                    retval.addDeletedService(newServiceEndpoint(service));
                }
            }
        }
        //new ones
        for (ConsulHealthEntry entry : healthEntries) {
            if (! discoveredServices.containsKey(entry.getId())) {
                retval.addNewService(newServiceEndpoint(entry));
                discoveredServices.put(entry.getId(), entry);
            }
        }
        //changes
        for (ConsulHealthEntry health : healthEntries) {
            ConsulHealthEntry previous = discoveredServices.get(health.getId());
            if (previous == null) {
                continue;
            }
            //we map 3 states into 2
            if (health.getStatus().equals(ConsulHealthEntry.Status.Passing) &&
                    previous.getStatus().equals(ConsulHealthEntry.Status.Critical)) {
                previous.setStatus(ConsulHealthEntry.Status.Passing);
                retval.addUpdatedService(newServiceEndpoint(previous));
            } else if (! health.getStatus().equals(ConsulHealthEntry.Status.Passing) &&
                    previous.getStatus().equals(ConsulHealthEntry.Status.Passing)) {
                previous.setStatus(ConsulHealthEntry.Status.Critical);
                retval.addUpdatedService(newServiceEndpoint(previous));
            }
        }
        return retval;
    }

    protected ServiceEndpoint newServiceEndpoint(ConsulHealthEntry entry) {
        ServiceEndpoint retval = new ServiceEndpoint(executor,
                entry.getAddressAndPort(), entry.getAvailZone());
        if (ConsulHealthEntry.Status.Passing.equals(entry.getStatus())) {
            //TODO: this might need some more work.  a flapping service in consul should
            //not bypass normal circuit breaker logic.
            retval.setCircuitBreakerState(CircuitBreakerState.State.PRIMARY_HEALTHY);
        } else {
            retval.setCircuitBreakerState(CircuitBreakerState.State.UNHEALTHY);
        }
        return retval;
    }

    public void setLoadbalancer(LoadBalancer loadbalancer) {
        this.loadbalancer = loadbalancer;
    }

}
