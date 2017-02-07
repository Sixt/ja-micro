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

package com.sixt.service.framework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//DO NOT MAKE @Singleton
public class MetricBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MetricBuilder.class);

    private final MetricRegistry registry;
    private final List<MetricTag> tags = new ArrayList<>();

    private String baseName;

    @Inject
    public MetricBuilder(MetricRegistry registry) {
        this.registry = registry;
    }

    public MetricBuilder withTag(String name, String value) {
        tags.add(new MetricTag(name, value));
        return this;
    }

    public synchronized GoTimer buildTimer() {
        String name = generateName("timing");
        GoTimer timer = getExistingTimer(name);
        if (timer == null) {
            timer = new GoTimer(name);
            Map<String, Metric> map = new HashMap<>(1);
            map.put(name, timer);
            MetricSet set = () -> map;
            try {
                registry.registerAll(set);
            } catch (Exception ex) {
                //I haven't figured out a good solution around this...
            }
        }
        return timer;
    }

    public synchronized GoCounter buildCounter() {
        String name = generateName("counter");
        GoCounter counter = getExistingCounter(name);
        if (counter == null) {
            counter = new GoCounter(name);
            Map<String, Metric> map = new HashMap<>();
            map.put(name, counter);
            MetricSet set = () -> map;
            registry.registerAll(set);
        }
        return counter;
    }

    public synchronized GoGauge buildGauge() {
        String name = generateName("gauge");
        GoGauge gauge = getExistingGauge(name);
        if (gauge == null) {
            gauge = new GoGauge(name);
            Map<String, Metric> map = new HashMap<>();
            map.put(name, gauge);
            MetricSet set = () -> map;
            registry.registerAll(set);
        }
        return gauge;
    }

    private GoTimer getExistingTimer(String name) {
        Metric m = registry.getMetrics().get(name);
        if (m instanceof GoTimer) {
            return (GoTimer) m;
        } else if (m != null) {
            logger.warn("Existing metric with name {} is not a GoTimer", name);
        }
        return null;
    }

    private GoCounter getExistingCounter(String name) {
        Metric m = registry.getMetrics().get(name);
        if (m instanceof GoCounter) {
            return (GoCounter) m;
        } else if (m != null) {
            logger.warn("Existing metric with name {} is not a GoCounter", name);
        }
        return null;
    }

    private GoGauge getExistingGauge(String name) {
        Metric m = registry.getMetrics().get(name);
        if (m instanceof GoGauge) {
            return (GoGauge) m;
        } else if (m != null) {
            logger.warn("Existing metric with name {} is not a GoGauge", name);
        }
        return null;
    }

    //this name is highly-dependent upon the Sixt GoOrange metrics infrastructure
    //TODO: when we implement pluggable metrics reporting, the metrics reporter
    //      should interact here...
    protected String generateName(String metricType) {
        if (baseName == null || baseName.isEmpty()) {
            throw new IllegalStateException("No baseName was provided");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(baseName);
        for (MetricTag tag : tags) {
            sb.append(",");
            sb.append(tag.name);
            sb.append("=");
            sb.append(tag.value);
        }
        sb.append(",metric_type=");
        sb.append(metricType);
        return sb.toString();
    }

    void setBaseName(String baseName) {
        this.baseName = baseName;
    }
}
