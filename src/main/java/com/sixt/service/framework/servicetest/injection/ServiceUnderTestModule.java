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

package com.sixt.service.framework.servicetest.injection;

import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.servicetest.service.ServiceUnderTestLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceUnderTestModule extends TestInjectionModule {

    private static final Logger logger = LoggerFactory.getLogger(ServiceUnderTestModule.class);

    public ServiceUnderTestModule(String serviceName) {
        this(serviceName, new ServiceProperties());
    }

    public ServiceUnderTestModule(String serviceName, ServiceProperties props) {
        super(serviceName, props);
    }

    @Override
    protected void configure() {
        bind(ServiceProperties.class).toInstance(serviceProperties);
        bind(LoadBalancer.class).to(ServiceUnderTestLoadBalancer.class);
    }

}
