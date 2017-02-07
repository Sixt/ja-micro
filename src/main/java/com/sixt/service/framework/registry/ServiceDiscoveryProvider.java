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

package com.sixt.service.framework.registry;

import com.sixt.service.framework.rpc.LoadBalancer;

public interface ServiceDiscoveryProvider {

    /**
     * Called when a LoadBalancer is created for a service dependency.
     * Typically the implementer should start up a thread to monitor the
     * service, and when state about the instances change, the LoadBalancer
     * is notified of the changes by the updateServiceEndpoints call.
     */
    void monitorService(LoadBalancer lb);

}
