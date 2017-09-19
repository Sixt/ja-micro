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

    public synchronized int internalPortForImpersonated(String service) {
        Integer retval = ports.get(service);
        if (retval == null) {
            ports.put(service, nextPort);
            retval = nextPort;
            nextPort++;
        }
        logger.debug("Returning port {} for service {}", retval, service);
        return retval;
    }

}
