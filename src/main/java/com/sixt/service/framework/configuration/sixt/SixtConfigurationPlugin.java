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

package com.sixt.service.framework.configuration.sixt;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.configuration.api.ConfigurationOuterClass;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.annotation.ConfigurationPlugin;
import com.sixt.service.framework.configuration.ConfigurationManager;
import com.sixt.service.framework.configuration.ConfigurationProvider;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.rpc.RpcClient;
import com.sixt.service.framework.rpc.RpcClientFactory;
import com.sixt.service.framework.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@ConfigurationPlugin(name = "sixt")
public class SixtConfigurationPlugin implements ConfigurationProvider, Runnable {

    public final static int LONG_POLL_TIMEOUT = 900_000; //15 minutes

    private static final Logger logger = LoggerFactory.getLogger(SixtConfigurationPlugin.class);

    protected ConfigurationManager configManager;
    protected RpcClientFactory clientFactory;
    private final ServiceProperties serviceProps;
    private AtomicInteger lastChangeIndex = new AtomicInteger(0);
    protected Sleeper sleeper = new Sleeper();
    protected final String serviceName;

    @Inject
    public SixtConfigurationPlugin(ConfigurationManager cm, RpcClientFactory clientFactory,
                                ServiceProperties serviceProperties) {
        this.configManager = cm;
        this.clientFactory = clientFactory;
        this.serviceProps = serviceProperties;
        this.serviceName = serviceProperties.getServiceName();
    }

    @Override
    public void initialize() {
        Thread t = new Thread(this, "SixtConfigurationPlugin");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                performLongPoll();
            } catch (Exception ex) {
                logger.error("Caught exception performing longPoll", ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void performLongPoll() {
        ConfigurationOuterClass.Filter configRequest = ConfigurationOuterClass.Filter.newBuilder().
                setService(serviceName).
                setVersion(serviceProps.getServiceVersion()).
                setInstance(serviceProps.getServiceInstanceId()).
                setChangeIndex(lastChangeIndex.get()).build();
        boolean giveWarning = true;
        long sleepTime = 1000;
        ConfigurationOuterClass.ValuesResponse response;
        while (true) {
            try {
                RpcClient<ConfigurationOuterClass.ValuesResponse> client =
                        clientFactory.newClient("com.sixt.service.configuration",
                                "Configuration.GetValues", ConfigurationOuterClass.ValuesResponse.class).
                                withRetries(0).withTimeout(LONG_POLL_TIMEOUT).build();
                logger.debug("Calling configuration service, lastChangeIndex = {}", lastChangeIndex.get());
                response = client.callSynchronous(configRequest, new OrangeContext());
                logger.debug("Got configuration service response");
                break;
            } catch (RpcCallException ex) {
                if (giveWarning) {
                    logger.info("Getting configuration failed, will retry. {}", ex.toString());
                    giveWarning = false;
                }
                sleeper.sleepNoException(sleepTime);
                sleepTime = increaseSleepTime(sleepTime);
            }
        }
        if (response != null) {
            processValues(response.getValuesList());
            lastChangeIndex.set(response.getChangeIndex());
        }
    }

    //pseudo-exponential back-off
    private long increaseSleepTime(long sleepTime) {
        if (sleepTime == 1000) {
            return 4000;
        } else if (sleepTime == 4000) {
            return 16000;
        } else {
            return 60000;
        }
    }

    private void processValues(List<ConfigurationOuterClass.ValueResponse> valuesList) {
        Map<String, String> newValues = new HashMap<>(valuesList.size());
        for (ConfigurationOuterClass.ValueResponse cv : valuesList) {
            logger.debug("Got configuration, entry = {}, value = {}", cv.getName(), cv.getBaseValue());
            newValues.put(cv.getName(), cv.getBaseValue());
        }
        configManager.processValues(newValues);
    }

}
