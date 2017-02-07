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
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.annotation.ConfigurationPlugin;
import com.sixt.service.framework.configuration.ConfigurationProvider;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ConfigurationModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationModule.class);

    private final ServiceProperties serviceProperties;
    private Object plugin;
    private ConfigurationProvider provider;
    private List<String> configurationPlugins;

    public ConfigurationModule(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    @Override
    protected void configure() {
    }

    @Provides
    public ConfigurationProvider getProvider(Injector injector) {
        if (provider == null) {
            logger.debug("Searching for instance of ConfigurationProvider");
            Object plugin = getConfigurationPlugin(injector);
            if (plugin != null && plugin instanceof ConfigurationProvider) {
                logger.debug("Found ConfigurationProvider: {}", plugin.getClass().getSimpleName());
                provider = (ConfigurationProvider) plugin;
            } else {
                logger.info("No ConfigurationProvider will be used.");
            }
        }
        return provider;
    }

    private Object getConfigurationPlugin(Injector injector) {
        if (plugin == null) {
            plugin = findConfigurationPlugin(injector);
        }
        return plugin;
    }

    private Object findConfigurationPlugin(Injector injector) {
        Object retval = null;
        String pluginName = serviceProperties.getProperty("configuration");
        if (StringUtils.isBlank(pluginName)) {
            return null;
        }
        if (configurationPlugins == null) { // only scan if not already set
            configurationPlugins = new FastClasspathScanner().scan().getNamesOfClassesWithAnnotation(ConfigurationPlugin.class);
        }
        boolean found = false;
        for (String plugin : configurationPlugins) {
            try {
                Class<? extends ConfigurationPlugin> pluginClass =
                        (Class<? extends ConfigurationPlugin>) Class.forName(plugin);
                ConfigurationPlugin anno = pluginClass.getAnnotation(ConfigurationPlugin.class);
                if (anno != null && pluginName.equals(anno.name())) {
                    retval = injector.getInstance(pluginClass);
                    found = true;
                    break;
                }
            } catch (ClassNotFoundException e) {
                logger.error("ConfigurationPlugin not found", e);
            }
        }
        if (! found) {
            logger.warn("Configuration plugin '{}' was not found in the class path", pluginName);
        }
        return retval;
    }

    public void setConfigurationPlugins(List<String> configurationPlugins) {
        this.configurationPlugins = configurationPlugins;
    }
}
