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

import java.util.List;
import java.util.Set;

//temporary class to debug consul issue
public class LoadBalancerDumper {

    private LoadBalancerFactory lbFactory;

    @Inject
    public LoadBalancerDumper(LoadBalancerFactory lbFactory) {
        this.lbFactory = lbFactory;
    }

    public String dumpCurrentState() {
        StringBuilder sb = new StringBuilder();
        Set<String> services = lbFactory.getServices();
        for (String svcName : services) {
            sb.append("Load balancer for: ").append(svcName).append("\n");
            LoadBalancer loadBalancer = lbFactory.getLoadBalancer(svcName);
            List<AvailabilityZone> zones = loadBalancer.getAvailabilityZones();
            for (AvailabilityZone az : zones) {
                sb.append("  Availability zone: ").append(az.getName()).append("\n");
                ServiceEndpointList endpointList = az.getServiceEndpoints();
                endpointList.debugDump(sb);
            }
        }
        return sb.toString();
    }

}
