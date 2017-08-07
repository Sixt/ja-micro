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

import java.util.List;

/**
 * Ja-micro has a client-side load balancer implementation bound by default. (LoadBalancerImpl)
 * This interface allows one to provide a different implementation.
 */
public interface LoadBalancer {

    /**
     * There is one LoadBalancer per service.  Set the service name for this instance.
     */
    void setServiceName(String serviceName);

    String getServiceName();

    /**
     * Used by the RpcClient to get a wrapper that directs calls to a specific instance
     * of a called service based on health.
     */
    HttpClientWrapper getHttpClientWrapper();

    /**
     * Get an instance of a called service that is likely to be healthy (more specifically:
     * an instance that is not known to not be healthy, unless it is a probe to allow the instance
     * to be healthy again.)
     */
    ServiceEndpoint getHealthyInstance();

    /**
     * Like above, but excluding ServiceEndpoints that the client has already tried.
     * Influenced by FeatureFlags.shouldDisableRpcInstanceRetry
     */
    ServiceEndpoint getHealthyInstanceExclude(List<ServiceEndpoint> triedEndpoints);

    /**
     * Pause the calling thread until an apparently healthy instance appears through
     * service discovery.
     */
    void waitForServiceInstance();

    /**
     * Used by the service registry watcher to let the LoadBalancer know about changes to
     * service instances based on registry information.
     */
    void updateServiceEndpoints(LoadBalancerUpdate updates);

}
