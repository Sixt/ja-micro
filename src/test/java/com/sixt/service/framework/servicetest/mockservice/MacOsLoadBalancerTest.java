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

package com.sixt.service.framework.servicetest.mockservice;

import com.sixt.service.framework.util.ProcessUtil;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MacOsLoadBalancerTest {

    private MacOsLoadBalancer loadBalancer = new MacOsLoadBalancer(mock(ProcessUtil.class));

    @Test
    public void parseExposedPortTest() {
        String json = "[{\"NetworkSettings\": {\"Ports\": {\"5005/tcp\": " +
                "[{\"HostIp\": \"0.0.0.0\",\"HostPort\": \"33240\"}]}}}]";
        int result = loadBalancer.parseExposedPort(json, 5005);
        assertThat(result).isEqualTo(33240);
    }

}
