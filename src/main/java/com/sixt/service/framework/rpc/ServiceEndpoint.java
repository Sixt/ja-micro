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

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class ServiceEndpoint {

    protected String availZone;
    protected String hostAndPort;
    protected CircuitBreakerState circuitBreaker;
    protected AtomicInteger servingRequests = new AtomicInteger(0); //intended only for probe logic

    public ServiceEndpoint(ScheduledThreadPoolExecutor executor,
                           String hostAndPort, String availZone) {
        this(hostAndPort, availZone, new CircuitBreakerState(executor));
    }

    public ServiceEndpoint(String hostAndPort, String availZone, CircuitBreakerState cb) {
        this.hostAndPort = hostAndPort;
        this.availZone = availZone;
        this.circuitBreaker = cb;
        if (this.availZone == null) {
            this.availZone = "";
        }
    }

    public String getHostAndPort() {
        return hostAndPort;
    }

    public String getAvailZone() {
        return availZone;
    }

    public void setCircuitBreakerState(CircuitBreakerState.State state) {
        this.circuitBreaker.setState(state);
    }

    public CircuitBreakerState.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    public boolean canServeRequests() {
        return circuitBreaker.canServeRequests(servingRequests.get() > 0);
    }

    public void incrementServingRequests() {
        servingRequests.incrementAndGet();
    }

    public void requestComplete(boolean success) {
        servingRequests.decrementAndGet();
        circuitBreaker.requestComplete(success);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceEndpoint that = (ServiceEndpoint) o;

        if (availZone != null ? !availZone.equals(that.availZone) : that.availZone != null) return false;
        return hostAndPort != null ? hostAndPort.equals(that.hostAndPort) : that.hostAndPort == null;

    }

    @Override
    public int hashCode() {
        int result = availZone != null ? availZone.hashCode() : 0;
        result = 31 * result + (hostAndPort != null ? hostAndPort.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return availZone + "/" + hostAndPort;
    }

}
