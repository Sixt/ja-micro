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

package com.sixt.service.framework.configuration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.ServiceProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class ConfigurationManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);

    protected ServiceProperties serviceProps;
    protected Multimap<String, ChangeCallback> callbackListeners;

    private Map<String, String> lastProperties;
    private AtomicBoolean dataReceived = new AtomicBoolean(false);
    private CountDownLatch startupLatch = new CountDownLatch(1);

    @Inject
    public ConfigurationManager(ServiceProperties props) {
        this.serviceProps = props;
        this.callbackListeners = Multimaps.synchronizedMultimap(ArrayListMultimap.create());
        this.lastProperties = new HashMap<>(props.getAllProperties());
    }

    public synchronized void processValues(Map<String, String> updatedValues) {
        updatedValues.keySet().stream()
                .forEach(key -> {
                    String newValue = updatedValues.get(key);
                    //process only new or updated values
                    if (! StringUtils.equals(newValue, lastProperties.get(key))) {
                        lastProperties.put(key, newValue);
                        notifyListeners(key, newValue);

                        //(allows command-line overrides)
                        serviceProps.addProperty(key, newValue);
                    }
                });
        dataReceived.set(true);
        startupLatch.countDown();
    }

    public void waitForInitialConfiguration() {
        logger.info("Waiting for initial service configuration");
        while (!dataReceived.get()) {
            try {
                startupLatch.await();
            } catch (InterruptedException e) {
            }
        }
        logger.info("Received initial service configuration");
    }

    public void registerChangeCallback(String entry, ChangeCallback callback) {
        callbackListeners.put(entry, callback);
    }

    private void notifyListeners(String key, String value) {
        synchronized (callbackListeners) {
            Collection<ChangeCallback> listeners = callbackListeners.get(key);
            if (listeners != null) {
                for (ChangeCallback listener : listeners) {
                    try {
                        listener.entryChanged(key, value);
                    } catch (Exception ex) {
                        logger.error("Error notifying listener of configuration change", ex);
                    }
                }
            }
        }
    }

}
