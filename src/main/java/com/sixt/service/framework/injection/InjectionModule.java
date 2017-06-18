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

package com.sixt.service.framework.injection;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.sixt.service.framework.FeatureFlags;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.configuration.ConfigurationManager;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InjectionModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(InjectionModule.class);

    private final MetricRegistry metricRegistry;
    private ServiceProperties serviceProperties;
    private final HttpClient httpClient;
    private MethodHandlerDictionary methodHandlerDictionary;
    private ConfigurationManager configurationManager;

    public InjectionModule(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
        metricRegistry = new MetricRegistry();
        JmxReporter reporter = JmxReporter.forRegistry(metricRegistry).build();
        reporter.start();
        httpClient = createHttpClient();
    }

    @Override
    protected void configure() {
        bind(ServiceProperties.class).toInstance(serviceProperties);
        bind(HttpClient.class).toInstance(httpClient);
    }

    @Provides
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    @Provides
    public MethodHandlerDictionary getMethodHandlers() {
        return methodHandlerDictionary;
    }

    @Provides
    public ExecutorService getExecutorService() {
        return Executors.newCachedThreadPool();
    }

    @Provides
    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public void setMethodHandlerDictionary(MethodHandlerDictionary methodHandlerDictionary) {
        this.methodHandlerDictionary = methodHandlerDictionary;
    }

    public void setConfigurationManager(ConfigurationManager cm) {
        //we need to share the instance created by the config plugin injector
        if (cm == null) {
            logger.info("ConfigurationManager was null!");
            if (serviceProperties == null) {
                logger.info("serviceProperties was null!");
                serviceProperties = new ServiceProperties();
            }
            this.configurationManager = new ConfigurationManager(serviceProperties);
        } else {
            this.configurationManager = cm;
        }
    }

    private HttpClient createHttpClient() {
        //Allow ssl by default
        SslContextFactory sslContextFactory = new SslContextFactory();
        //Don't exclude RSA because Sixt needs them, dammit!
        sslContextFactory.setExcludeCipherSuites("");
        HttpClient client = new HttpClient(sslContextFactory);
        client.setFollowRedirects(false);
        client.setMaxConnectionsPerDestination(16);
        client.setConnectTimeout(FeatureFlags.getHttpConnectTimeout(serviceProperties));
        client.setAddressResolutionTimeout(FeatureFlags.getHttpAddressResolutionTimeout(serviceProperties));
        //You can set more restrictive timeouts per request, but not less, so
        //  we set the maximum timeout of 1 hour here.
        client.setIdleTimeout(60 * 60 * 1000);
        try {
            client.start();
        } catch (Exception e) {
            logger.error("Error building http client", e);
        }
        return client;
    }

}
