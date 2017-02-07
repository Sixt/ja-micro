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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceEndpointListTest {

    private ServiceEndpointList list = new ServiceEndpointList();

    @Test
    public void addNode() {
        ServiceEndpoint sep1 = new ServiceEndpoint(null, "1.1.1.1:80", "dc1");
        ServiceEndpoint sep2 = new ServiceEndpoint(null, "1.1.1.2:80", "dc1");
        ServiceEndpoint sep3 = new ServiceEndpoint(null, "1.1.1.3:80", "dc1");
        list.add(sep1);
        list.add(sep2);
        list.add(sep3);
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.nextAvailable()).isEqualTo(sep3);
        assertThat(list.nextAvailable()).isEqualTo(sep1);
        assertThat(list.nextAvailable()).isEqualTo(sep2);
        assertThat(list.nextAvailable()).isEqualTo(sep3);
        list.updateEndpointHealth(new ServiceEndpoint(null, "1.1.1.2:80", "dc1"),
                CircuitBreakerState.State.UNHEALTHY);
        list.updateEndpointHealth(new ServiceEndpoint(null, "11.11.11.12:80", "dc1"),
                CircuitBreakerState.State.UNHEALTHY);
    }

    @Test
    public void verifyToString() {
        ServiceEndpoint sep1 = new ServiceEndpoint(null, "1.1.1.1:80", "dc1");
        ServiceEndpointNode node = new ServiceEndpointNode(sep1);
        assertThat(node.toString()).isEqualTo(sep1.toString());
    }

}