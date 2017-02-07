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

import java.util.ArrayList;
import java.util.List;

public class LoadBalancerUpdate {

    protected List<ServiceEndpoint> newServices = new ArrayList<>();
    protected List<ServiceEndpoint> updatedServices = new ArrayList<>();
    protected List<ServiceEndpoint> deletedServices = new ArrayList<>();

    public void addNewService(ServiceEndpoint ep) {
        newServices.add(ep);
    }

    public void addUpdatedService(ServiceEndpoint ep) {
        updatedServices.add(ep);
    }

    public void addDeletedService(ServiceEndpoint ep) {
        deletedServices.add(ep);
    }

    public List<ServiceEndpoint> getNewServices() {
        return newServices;
    }

    public List<ServiceEndpoint> getUpdatedServices() {
        return updatedServices;
    }

    public List<ServiceEndpoint> getDeletedServices() {
        return deletedServices;
    }

    public boolean isEmpty() {
        return newServices.isEmpty() && updatedServices.isEmpty() &&
                deletedServices.isEmpty();
    }

}
