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
import com.sixt.service.framework.FeatureFlags;
import com.sixt.service.framework.ServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static net.logstash.logback.marker.Markers.append;

public class LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);

    protected ServiceProperties serviceProps;
    protected HttpClientWrapper httpClientWrapper;
    protected String serviceName;
    //we don't expect more than 3, so hashmap doesn't necessarily make sense
    protected List<AvailabilityZone> availabilityZones = new ArrayList<>();
    protected ReentrantReadWriteLock mutex = new ReentrantReadWriteLock();
    protected Semaphore notificationSemaphore = new Semaphore(0);
    protected AtomicBoolean haveEndpoints = new AtomicBoolean(false);

    @Inject
    public LoadBalancer(ServiceProperties serviceProps,
                        HttpClientWrapper wrapper) {
        this.serviceProps = serviceProps;
        this.httpClientWrapper = wrapper;
        httpClientWrapper.setLoadBalancer(this);
    }

    public HttpClientWrapper getHttpClientWrapper() {
        return httpClientWrapper;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void updateServiceEndpoints(LoadBalancerUpdate updates) {
        mutex.writeLock().lock();
        try {
            Marker logMarker = append("serviceName", this.serviceName);
            for (ServiceEndpoint ep : updates.getNewServices()) {
                logger.debug(logMarker,
                        "Endpoint for {} became available: {}", this.serviceName, ep.getHostAndPort());
                addServiceEndpoint(ep);
            }
            for (ServiceEndpoint ep : updates.getDeletedServices()) {
                logger.debug(logMarker,
                        "Endpoint for {} became unavailable: {}", this.serviceName, ep.getHostAndPort());
                updateEndpointHealth(ep, CircuitBreakerState.State.UNHEALTHY);
            }
            for (ServiceEndpoint ep : updates.getUpdatedServices()) {
                logger.debug(logMarker,
                        "Health of endpoint {} of {} changed to {}", ep.getHostAndPort(), this.serviceName,
                        ep.getCircuitBreakerState());
                updateEndpointHealth(ep, ep.getCircuitBreakerState());
            }
        } finally {
            mutex.writeLock().unlock();
        }
    }

    /**
     * We rely on the object that populates us to order availability zones
     * with primary first, then going nearest to furthest. (implying priority)
     * Only to be used from this class or tests
     */
    protected void addServiceEndpoint(ServiceEndpoint endpoint) {
        boolean found = false;
        for (AvailabilityZone az : availabilityZones) {
            if (az.getName().equals(endpoint.getAvailZone())) {
                az.addServiceEndpoint(endpoint);
                found = true;
            }
        }
        if (! found) {
            AvailabilityZone az = new AvailabilityZone();
            az.addServiceEndpoint(endpoint);
            availabilityZones.add(az);
        }
        haveEndpoints.set(true);
        notificationSemaphore.release();
    }

    protected void updateEndpointHealth(ServiceEndpoint ep, CircuitBreakerState.State state) {
        for (AvailabilityZone az : availabilityZones) {
            if (az.getName().equals(ep.getAvailZone())) {
                az.updateEndpointHealth(ep, state);
                return;
            }
        }
        logger.error("updateEndpointHealth: availZone {} not found", ep.getAvailZone());
    }

    protected int getAvailabilityZoneCount() {
        return availabilityZones.size();
    }

    /**
     * Try to find an endpoint in our primary AZ.  If none found, try further AZs.
     * Modifies state
     */
    public ServiceEndpoint getHealthyInstance() {
        if (! haveEndpoints.get()) {
            //wait for the first one to come in
            try {
                notificationSemaphore.tryAcquire(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
        mutex.readLock().lock();
        try {
            for (AvailabilityZone az : availabilityZones) {
                ServiceEndpoint next = az.nextEndpoint();
                if (next != null) {
                    logger.debug("Returning instance {} for {}", next.getHostAndPort(), serviceName);
                    return next;
                }
            }
            return null;
        } finally {
            mutex.readLock().unlock();
        }
    }

    //modifies state
    public ServiceEndpoint getHealthyInstanceExclude(List<ServiceEndpoint> triedEndpoints) {
        mutex.readLock().lock();
        try {
            Set<ServiceEndpoint> set = new HashSet<>(triedEndpoints);
            Set<ServiceEndpoint> seenInstances = new HashSet<>();
            while (true) {
                ServiceEndpoint retval = getHealthyInstance();
                if (!FeatureFlags.shouldDisableRpcInstanceRetry(serviceProps)) {
                    if (seenInstances.contains(retval)) {
                        //we've made a complete loop
                        return null;
                    }
                    if (set.contains(retval)) {
                        seenInstances.add(retval);
                        continue;
                    }
                }
                return retval;
            }
        } finally {
            mutex.readLock().unlock();
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void waitForServiceInstance() {
        while (true) {
            if (getHealthyInstance() != null) {
                break;
            }
            try {
                notificationSemaphore.tryAcquire(1, 100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.warn("Thread was interrupted", e);
                break;
            }
        }
        logger.debug("Found service instance of {}", serviceName);
    }

    //intended only for debugging
    public List<AvailabilityZone> getAvailabilityZones() {
        return availabilityZones;
    }

}

class AvailabilityZone {

    private String name = "";
    private ServiceEndpointList serviceEndpoints = new ServiceEndpointList();

    public String getName() {
        return name;
    }

    public void addServiceEndpoint(ServiceEndpoint endpoint) {
        if (serviceEndpoints.isEmpty()) {
            this.name = endpoint.getAvailZone();
        }
        serviceEndpoints.add(endpoint);
    }

    //modifies state
    public ServiceEndpoint nextEndpoint() {
        return serviceEndpoints.nextAvailable();
    }

    public void updateEndpointHealth(ServiceEndpoint ep, CircuitBreakerState.State state) {
        serviceEndpoints.updateEndpointHealth(ep, state);
    }

    //intended only for debugging
    public ServiceEndpointList getServiceEndpoints() {
        return serviceEndpoints;
    }

}
