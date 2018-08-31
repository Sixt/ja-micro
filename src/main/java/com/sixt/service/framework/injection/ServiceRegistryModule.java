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

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.annotation.ServiceRegistryPlugin;
import com.sixt.service.framework.registry.ServiceDiscoveryProvider;
import com.sixt.service.framework.registry.ServiceRegistrationProvider;
import io.github.classgraph.ClassInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ServiceRegistryModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistryModule.class);

    private final ServiceProperties serviceProperties;
    private Object plugin;
    private ServiceRegistrationProvider registrationProvider;
    private ServiceDiscoveryProvider discoveryProvider;
    private List<ClassInfo> serviceRegistryPlugins;

    public ServiceRegistryModule(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    @Override
    protected void configure() {
    }

    @Provides
    public ServiceDiscoveryProvider getDiscoveryProvider(Injector injector) {
        if (discoveryProvider == null) {
            Object plugin = getServiceRegistryPlugin(injector);
            if (plugin != null && plugin instanceof ServiceDiscoveryProvider) {
                discoveryProvider = (ServiceDiscoveryProvider) plugin;
            }
        }
        return discoveryProvider;
    }

    @Provides
    public ServiceRegistrationProvider getRegistrationProvider(Injector injector) {
        if (registrationProvider == null) {
            logger.debug("Searching for instance of ServiceRegistrationProvider");
            Object plugin = getServiceRegistryPlugin(injector);
            if (plugin != null && plugin instanceof ServiceRegistrationProvider) {
                logger.debug("Found ServiceRegistrationProvider: {}", plugin.getClass().getSimpleName());
                registrationProvider = (ServiceRegistrationProvider) plugin;
                ServiceRegistrationProvider provider = (ServiceRegistrationProvider) plugin;
                MethodHandlerDictionary methodHandlers = injector.getInstance(MethodHandlerDictionary.class);
                provider.initialize(methodHandlers);
            }
        }
        return registrationProvider;
    }

    private Object getServiceRegistryPlugin(Injector injector) {
        if (plugin == null) {
            plugin = findServiceRegistryPlugin(injector);
        }
        return plugin;
    }

    private Object findServiceRegistryPlugin(Injector injector) {
        Object retval = null;
        String pluginName = serviceProperties.getProperty("registry");
        if (StringUtils.isBlank(pluginName)) {
            return null;
        }
        if (serviceRegistryPlugins == null) {
            logger.warn("No service registry plugins were configured");
            return null;
        }
        boolean found = false;
        for (ClassInfo plugin : serviceRegistryPlugins) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends ServiceRegistryPlugin> pluginClass =
                        (Class<? extends ServiceRegistryPlugin>) plugin.loadClass();
                ServiceRegistryPlugin anno = pluginClass.getAnnotation(ServiceRegistryPlugin.class);
                if (anno != null && pluginName.equals(anno.name())) {
                    retval = injector.getInstance(pluginClass);
                    found = true;
                    break;
                }
            } catch (IllegalArgumentException e) {
                logger.error("ServiceRegistryPlugin not found", e);
            }
        }
        if (! found) {
            logger.warn("Registry plugin '{}' was not found in the class path", pluginName);
        }
        return retval;
    }

    public void setServiceRegistryPlugins(List<ClassInfo> serviceRegistryPlugins) {
        this.serviceRegistryPlugins = serviceRegistryPlugins;
    }

}
