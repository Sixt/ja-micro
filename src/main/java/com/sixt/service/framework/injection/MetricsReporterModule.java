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
import com.sixt.service.framework.annotation.MetricsReporterPlugin;
import com.sixt.service.framework.metrics.MetricsReporterProvider;
import io.github.classgraph.ClassInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MetricsReporterModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(MetricsReporterModule.class);

    private List<ClassInfo> metricsReporterPlugins;
    private Object plugin;
    private MetricsReporterProvider provider;

    @Override
    protected void configure() {
    }

    @Provides
    public MetricsReporterProvider getProvider(Injector injector) {
        if (provider == null) {
            Object plugin = getPlugin(injector);
            if (plugin != null && plugin instanceof MetricsReporterProvider) {
                provider = (MetricsReporterProvider) plugin;
            }
        }
        return provider;
    }

    private Object getPlugin(Injector injector) {
        if (plugin == null) {
            plugin = findPlugin(injector);
        }
        return plugin;
    }

    private Object findPlugin(Injector injector) {
        Object retval = null;
        ServiceProperties serviceProperties = injector.getInstance(ServiceProperties.class);
        String pluginName = serviceProperties.getProperty("metricsReporter");
        if (StringUtils.isBlank(pluginName)) {
            return null;
        }
        if (metricsReporterPlugins == null) {
            logger.warn("No metrics reporting plugins were configured");
            return null;
        }
        boolean found = false;
        for (ClassInfo plugin : metricsReporterPlugins) {
            try {
                @SuppressWarnings("unchecked") Class<? extends MetricsReporterPlugin> pluginClass =
                        (Class<? extends MetricsReporterPlugin>) plugin.loadClass();
                MetricsReporterPlugin anno = pluginClass.getAnnotation(MetricsReporterPlugin.class);
                if (anno != null && pluginName.equals(anno.name())) {
                    retval = injector.getInstance(pluginClass);
                    found = true;
                    break;
                }
            } catch (IllegalArgumentException e) {
                logger.error("MetricsReporterPlugin not found", e);
            }
        }
        if (! found) {
            logger.warn("Metrics reporting plugin '{}' was not found in the class path", pluginName);
        }
        return retval;
    }

    public void setMetricsReporterPlugins(List<ClassInfo> plugins) {
        this.metricsReporterPlugins = plugins;
    }

}
