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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ImpersonatedPortDictionary {

    private static final Logger logger = LoggerFactory.getLogger(ImpersonatedPortDictionary.class);

    //Note: I hate static singletons, but since ServiceUnderTest and each impersonator
    //  need their own injection stacks, so it's difficult not to use statics here

    private static ImpersonatedPortDictionary instance;
    private int nextPort = 42000;
    private Map<String, Integer> ports = new HashMap<>();

    private ImpersonatedPortDictionary() {
    }

    public synchronized static ImpersonatedPortDictionary getInstance() {
        if (instance == null) {
            instance = new ImpersonatedPortDictionary();
        }
        return instance;
    }

    public synchronized int getInternalPortForImpersonated(String service) {
        Integer retval = ports.get(service);
        if (retval == null) {
            throw new IllegalStateException("Service " + service + " is unknown. Was it registered correctly?");
        }
        logger.debug("Returning port {} for service {}", retval, service);
        return retval;
    }

    public synchronized int newInternalPortForImpersonated(String service) {
        int retval = nextPort;
        ports.put(service, retval);
        nextPort++;
        logger.debug("Using port {} for service {}", retval, service);
        return retval;
    }

}
