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

package com.sixt.service.framework;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.protobuf.Message;
import com.sixt.service.framework.annotation.RpcHandler;
import com.sixt.service.framework.configuration.ConfigurationManager;
import com.sixt.service.framework.configuration.ConfigurationProvider;
import com.sixt.service.framework.configuration.LogLevelChangeCallback;
import com.sixt.service.framework.database.SchemaMigrator;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.health.HealthCheckContributor;
import com.sixt.service.framework.health.HealthCheckManager;
import com.sixt.service.framework.injection.*;
import com.sixt.service.framework.jetty.JettyComposer;
import com.sixt.service.framework.jetty.RpcServlet;
import com.sixt.service.framework.logging.SixtLogbackContext;
import com.sixt.service.framework.metrics.MetricsReporterProvider;
import com.sixt.service.framework.registry.ServiceDiscoveryProvider;
import com.sixt.service.framework.registry.ServiceRegistrationProvider;
import com.sixt.service.framework.rpc.LoadBalancerFactory;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractService {

    private static final Logger logger = LoggerFactory.getLogger(AbstractService.class);

    protected MethodHandlerDictionary methodHandlers = new MethodHandlerDictionary();

    protected ServiceProperties serviceProperties = new ServiceProperties();
    protected Server jettyServer;
    protected Injector injector = null;
    protected AtomicBoolean startupComplete = new AtomicBoolean(false);
    private ConfigurationManager configurationManager;
    private List<String> serviceRegistryPlugins;
    private List<String> configurationPlugins;
    private List<String> metricsReporterPlugins;
    private List<String> tracingPlugins;

    @SuppressWarnings("unchecked")
    public void registerMethodHandlers(List<String> rpcHandlers) {
        for (String className : rpcHandlers) {
            try {
                Class clazz = Class.forName(className);

                if ((clazz != ServiceMethodHandler.class) && ServiceMethodHandler.class.isAssignableFrom(clazz)) {
                    registerMethodHandlerFor(((RpcHandler) clazz.getAnnotation(RpcHandler.class)).value(), clazz);
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "RpcHandler annotation applied to class %s that does not implement ServiceMethodHandler",
                                    clazz.getName()
                            )
                    );
                }
            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * @deprecated please use displayHelp(PrintStream out) instead
     */
    @Deprecated
    public void displayHelp() {
        displayHelp(System.out);
    }

    public abstract void displayHelp(PrintStream out);

    /**
     * Supplies additional modules specific to this service.
     * Provide sub-class implementation to override
     */
    protected List<Module> getGuiceModules() {
        return null;
    }

    public void initializeGuice() throws Exception {
        if (injector == null) {
            InjectionModule mainModule = new InjectionModule(serviceProperties);
            mainModule.setConfigurationManager(configurationManager);
            mainModule.setMethodHandlerDictionary(methodHandlers);
            ServiceRegistryModule registryModule = new ServiceRegistryModule(serviceProperties);
            registryModule.setServiceRegistryPlugins(serviceRegistryPlugins);
            MetricsReporterModule metricsModule = new MetricsReporterModule();
            metricsModule.setPlugins(metricsReporterPlugins);
            TracingModule tracingModule = new TracingModule(serviceProperties);
            tracingModule.setPlugins(tracingPlugins);

            List<Module> modules = getGuiceModules();
            if (modules == null) {
                modules = new ArrayList<>();
            } else {
                modules = new ArrayList<>(modules);
            }

            modules.add(0, new OrangeServletModule());
            modules.add(0, registryModule);
            modules.add(0, metricsModule);
            modules.add(0, tracingModule);
            modules.add(0, mainModule);
            //^^^ the order of the modules is specifically controlled here
            injector = Guice.createInjector((Module[]) modules.toArray(new Module[0]));
        }
    }

    public void initProperties(String[] args) {
        serviceProperties.initialize(args);
    }

    /**
     * Override to verify any required command-line parameters or environment
     * variables have been set.
     */
    public void verifyEnvironment() {
    }

    public void startJettyContainer() throws Exception {
        jettyServer = new Server(serviceProperties.getServicePort());
        JettyComposer.compose(jettyServer);
        jettyServer.start();
        int port = ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();
        logger.info("Jetty has started on port {}", port);
        serviceProperties.setServicePort(port);
    }

    public void bootstrapComplete() throws InterruptedException {
        startupComplete.set(true);
        injector.getInstance(RpcServlet.class).serveRequests();
        jettyServer.join();
    }

    @SuppressWarnings("unchecked")
    public void initializeHealthCheckManager(List<String> hcProviders) {
        if (hcProviders != null && !hcProviders.isEmpty()) {
            HealthCheckManager hcManager = injector.getInstance(HealthCheckManager.class);
            for (String hcp : hcProviders) {
                try {
                    Class<HealthCheckContributor> hcClass = (Class<HealthCheckContributor>) Class.forName(hcp);
                    logger.debug("Found HealthCheckContributor: {}", hcClass.getSimpleName());
                    HealthCheckContributor contrib = injector.getInstance(hcClass);
                    if (contrib.shouldRegister()) {
                        hcManager.registerPollingContributor(contrib);
                    } else {
                        logger.debug("HealthCheckContributor is choosing not to join");
                    }
                } catch (ClassNotFoundException e) {
                    logger.error("Error loading HealthCheckProvider: " + hcp, e);
                }
            }
            hcManager.registerOneShotContributor(new HealthCheckContributor() {
                @Override
                public HealthCheck getHealthCheck() {
                    if (startupComplete.get()) {
                        return new HealthCheck();
                    } else {
                        return new HealthCheck("service_startup_completion",
                                HealthCheck.Status.FAIL, "Service startup not complete");
                    }
                }

                @Override
                public boolean shouldRegister() {
                    return true;
                }
            });
            hcManager.initialize();
        }
    }

    public void performDatabaseMigration() {
        SchemaMigrator migrator = injector.getInstance(SchemaMigrator.class);
        migrator.migrate();
    }

    /**
     * Override this method to register method handlers at startup.
     * Classpath-scanning is now also offered to find @RpcHandlers
     */
    public void registerMethodHandlers() {
    }

    @SuppressWarnings("unchecked")
    protected void registerMethodHandlerFor(String endpoint,
                                            Class<? extends ServiceMethodHandler> handlerClass) {
        methodHandlers.put(endpoint, injector.getInstance(handlerClass));
    }

    protected void registerPreMethodHandlerHookFor(String endpoint,
            Class<? extends ServiceMethodPreHook<? extends Message>> handlerClass) {
        methodHandlers.addPreHook(endpoint, injector.getInstance(handlerClass));
    }

    protected void registerPostMethodHandlerHookFor(String endpoint,
            Class<? extends ServiceMethodPostHook<? extends Message>> handlerClass) {
        methodHandlers.addPostHook(endpoint, injector.getInstance(handlerClass));
    }

    public Map<String, ServiceMethodHandler<? extends Message, ? extends Message>> getMethodHandlers() {
        return methodHandlers.getMethodHandlers();
    }

    @VisibleForTesting
    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    public ServiceProperties getServiceProperties() {
        return serviceProperties;
    }

    public void enableJmx() {
        System.setProperty("com.sun.management.jmxremote.ssl", "false");
        System.setProperty("com.sun.management.jmxremote.authenticate", "false");
        System.setProperty("com.sun.management.jmxremote.port", "1099");
    }

    public void initializeServiceDiscovery() {
        initializeLoadBalancerFactory(injector);
    }

    public void initializeServiceRegistration() {
        injector.getInstance(ServiceRegistrationProvider.class);
    }

    public void initializeConfigurationManager() {
        if (StringUtils.equals(serviceProperties.getServiceName(), "com.sixt.service.configuration")) {
            return;
        }

        InjectionModule configBaseModule = new InjectionModule(serviceProperties);
        configurationManager = new ConfigurationManager(serviceProperties);
        configBaseModule.setConfigurationManager(configurationManager);

        ServiceRegistryModule serviceRegistryModule = new ServiceRegistryModule(serviceProperties);
        serviceRegistryModule.setServiceRegistryPlugins(serviceRegistryPlugins);
        ConfigurationModule configurationModule = new ConfigurationModule(serviceProperties);
        configurationModule.setConfigurationPlugins(configurationPlugins);

        Injector configInjector = Guice.createInjector(configBaseModule,
                serviceRegistryModule,
                configurationModule);

        ConfigurationProvider configProvider = configInjector.getInstance(ConfigurationProvider.class);
        if (configProvider == null) {
            return;
        }

        initializeLoadBalancerFactory(configInjector);
        configurationManager.registerChangeCallback(ServiceProperties.LOG_LEVEL_KEY,
                new LogLevelChangeCallback(new SixtLogbackContext()));
        configProvider.initialize();
        configurationManager.waitForInitialConfiguration();
    }

    private void initializeLoadBalancerFactory(Injector localInjector) {
        ServiceDiscoveryProvider provider = localInjector.getInstance(ServiceDiscoveryProvider.class);
        if (provider != null) {
            LoadBalancerFactory lbFactory = localInjector.getInstance(LoadBalancerFactory.class);
            lbFactory.initialize(provider);
        }
    }

    void setServiceRegistryPlugins(List<String> serviceRegistryPlugins) {
        this.serviceRegistryPlugins = serviceRegistryPlugins;
    }

    void setConfigurationPlugins(List<String> configurationPlugins) {
        this.configurationPlugins = configurationPlugins;
    }

    public void setMetricsReporterPlugins(List<String> metricsReporterPlugins) {
        this.metricsReporterPlugins = metricsReporterPlugins;
    }

    public void initializeMetricsReporting() {
        MetricsReporterProvider provider = injector.getInstance(MetricsReporterProvider.class);
        if (provider != null) {
            provider.initialize();
        }
    }

    public void setTracingPlugins(List<String> tracingPlugins) {
        this.tracingPlugins = tracingPlugins;
    }
}
