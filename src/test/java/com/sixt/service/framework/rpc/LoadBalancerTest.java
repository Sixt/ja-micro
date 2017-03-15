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

import com.sixt.service.framework.ServiceProperties;
import org.eclipse.jetty.client.HttpClient;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class LoadBalancerTest {

    private LoadBalancer lb;

    @Before
    public void setup() {
        ServiceProperties properties = new ServiceProperties();
        HttpClient httpClient = mock(HttpClient.class);
        HttpClientWrapper wrapper = new HttpClientWrapper(properties, httpClient, null, null);
        lb = new LoadBalancer(properties, wrapper);
    }

    @Test
    public void multipleAvailZones() {
        ServiceEndpoint ep1 = new ServiceEndpoint(null, "1.1.1.1:80", "dc1");
        ServiceEndpoint ep2 = new ServiceEndpoint(null, "1.1.1.2:80", "dc2");
        ServiceEndpoint ep3 = new ServiceEndpoint(null, "1.1.1.3:80", "dc3");
        lb.addServiceEndpoint(ep1);
        lb.addServiceEndpoint(ep2);
        lb.addServiceEndpoint(ep3);
        assertThat(lb.getAvailabilityZoneCount()).isEqualTo(3);
    }

    @Test
    public void mostlyUnhealthySingleAz() {
        ServiceEndpoint ep1 = new ServiceEndpoint(null, "1.1.1.1:80", "dc1");
        ep1.setCircuitBreakerState(CircuitBreakerState.State.PRIMARY_TRIPPED);
        ServiceEndpoint ep2 = new ServiceEndpoint(null, "1.1.1.2:80", "dc1");
        ep2.setCircuitBreakerState(CircuitBreakerState.State.PRIMARY_TRIPPED);
        ServiceEndpoint ep3 = new ServiceEndpoint(null, "1.1.1.3:80", "dc1");
        ep3.setCircuitBreakerState(CircuitBreakerState.State.PRIMARY_TRIPPED);
        ServiceEndpoint ep4 = new ServiceEndpoint(null, "1.1.1.4:80", "dc1");
        ep4.setCircuitBreakerState(CircuitBreakerState.State.PRIMARY_TRIPPED);
        lb.addServiceEndpoint(ep1);
        lb.addServiceEndpoint(ep2);
        lb.addServiceEndpoint(ep3);
        lb.addServiceEndpoint(ep4);
        assertThat(lb.getHealthyInstance()).isNull();
        ServiceEndpoint ep5 = new ServiceEndpoint(null, "1.1.1.5:80", "dc1");
        lb.addServiceEndpoint(ep5);
        assertThat(lb.getHealthyInstance()).isEqualTo(ep5);
    }

    @Test
    public void probesOnlyGetOneRequest() {
        ServiceEndpoint ep1 = new ServiceEndpoint(null, "1.1.1.1:80", "dc1");
        ep1.setCircuitBreakerState(CircuitBreakerState.State.PRIMARY_TRIPPED);
        ServiceEndpoint ep2 = new ServiceEndpoint(null, "1.1.1.2:80", "dc1");
        ep2.setCircuitBreakerState(CircuitBreakerState.State.PRIMARY_PROBE);
        lb.addServiceEndpoint(ep1);
        lb.addServiceEndpoint(ep2);
        assertThat(lb.getHealthyInstance()).isEqualTo(ep2);
        assertThat(lb.getHealthyInstance()).isNull();
    }

}
