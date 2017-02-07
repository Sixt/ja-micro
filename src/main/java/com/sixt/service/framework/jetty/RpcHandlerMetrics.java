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

package com.sixt.service.framework.jetty;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.metrics.GoCounter;
import com.sixt.service.framework.metrics.GoTimer;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class RpcHandlerMetrics {

    private static final Logger logger = LoggerFactory.getLogger(RpcHandlerMetrics.class);

    public final static String UNKNOWN = "unknown";

    private final MetricBuilderFactory metricBuilderFactory;
    protected Map<String, GoCounter> counters = new HashMap<>();
    protected Map<String, GoTimer> timers = new HashMap<>();

    @Inject
    public RpcHandlerMetrics(MetricBuilderFactory metricBuilderFactory) {
        this.metricBuilderFactory = metricBuilderFactory;
    }

    public void incrementSuccessCounter(String methodName, String originService,
                                        String originMethod) {
        GoCounter counter = getOrCreateCounter(methodName, originService, originMethod);
        counter.incSuccess();
    }

    public void incrementFailureCounter(String methodName, String originService,
                                        String originMethod) {
        GoCounter counter = getOrCreateCounter(methodName, originService, originMethod);
        counter.incFailure();
    }

    private synchronized GoCounter getOrCreateCounter(String methodName, String originService, String originMethod) {
        if (StringUtils.isBlank(methodName)) {
            methodName = UNKNOWN;
        }
        if (StringUtils.isBlank(originService)) {
            originService = UNKNOWN;
        }
        if (StringUtils.isBlank(originMethod)) {
            originMethod = UNKNOWN;
        }
        String key = methodName + ":" + originService + ":" + originMethod;
        GoCounter counter = counters.get(key);
        if (counter == null) {
            counter = metricBuilderFactory.newMetric("server_handler").
                    withTag("method", methodName).
                    withTag("origin_service", originService).
                    withTag("origin_method", originMethod).buildCounter();
            counters.put(key, counter);
        }
        return counter;
    }

    public synchronized GoTimer getMethodTimer(String methodName, String originService, String originMethod) {
        if (StringUtils.isBlank(methodName)) {
            methodName = UNKNOWN;
        }
        if (StringUtils.isBlank(originService)) {
            originService = UNKNOWN;
        }
        if (StringUtils.isBlank(originMethod)) {
            originMethod = UNKNOWN;
        }
        String key = methodName + ":" + originService + ":" + originMethod;
        GoTimer timer = timers.get(key);
        if (timer == null) {
            timer = metricBuilderFactory.newMetric("server_handler").
                    withTag("method", methodName).
                    withTag("origin_service", originService).
                    withTag("origin_method", originMethod).buildTimer();
            timers.put(key, timer);
        }
        return timer;
    }

}
