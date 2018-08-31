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
import com.sixt.service.framework.annotation.*;
import com.sixt.service.framework.tracing.TracingProvider;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.opentracing.Tracer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TracingModule extends AbstractModule {

    public static final Logger logger = LoggerFactory.getLogger(TracingModule.class);
    private final ServiceProperties serviceProperties;
    private Tracer tracer;
    private Object plugin;
    private List<ClassInfo> tracingPlugins;

    public TracingModule(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    @Override
    protected void configure() {
    }

    @Provides
    public Tracer getTracer(Injector injector) {
        if (tracer == null) {
            Object plugin = getTracingPlugin(injector);
            if (plugin instanceof TracingProvider) {
                tracer = ((TracingProvider) plugin).getTracer();
            }
        }
        return tracer;
    }

    private Object getTracingPlugin(Injector injector) {
        if (plugin == null) {
            plugin = findTracingPlugin(injector);
        }
        return plugin;
    }

    @SuppressWarnings("unchecked")
    private Object findTracingPlugin(Injector injector) {
        Object retval = null;
        String pluginName = serviceProperties.getProperty("tracing");
        if (StringUtils.isBlank(pluginName)) {
            logger.debug("no tracing plugin set, defaulting to 'noop'");
            pluginName = "noop";
        }
        if (tracingPlugins == null) {
            //scanning is slow, this if should only be hit in tests.
            ScanResult scanResult = new ClassGraph().enableAllInfo().whitelistPackages("*").scan();
            tracingPlugins = scanResult.getClassesWithAnnotation(TracingPlugin.class.getName());
        }
        boolean found = false;
        for (ClassInfo plugin : tracingPlugins) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends TracingPlugin> pluginClass = (Class<? extends TracingPlugin>) plugin.loadClass();
                TracingPlugin anno = pluginClass.getAnnotation(TracingPlugin.class);
                if (anno != null && pluginName.equals(anno.name())) {
                    retval = injector.getInstance(pluginClass);
                    found = true;
                    break;
                }
            } catch (IllegalArgumentException e) {
                logger.error("Tracing plugin not found", e);
            }
        }
        if (! found) {
            logger.warn("Tracing plugin '{}' was not found in the class path", pluginName);
        }
        return retval;
    }

    public void setTracingPlugins(List<ClassInfo> plugins) {
        this.tracingPlugins = plugins;
    }

}
