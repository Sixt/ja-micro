package com.sixt.service.framework.servicetest.mockservice;

import java.util.HashMap;
import java.util.Map;

public class ImpersonatedPortDictionary {

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
        return retval;
    }

}
