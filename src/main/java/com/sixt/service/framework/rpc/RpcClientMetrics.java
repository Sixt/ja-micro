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

package com.sixt.service.framework.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.metrics.GoTimer;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RpcClientMetrics {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientMetrics.class);

    public final static String UNKNOWN = "unknown";

    private final MetricBuilderFactory metricBuilderFactory;

    @Inject
    public RpcClientMetrics(MetricBuilderFactory metricBuilderFactory) {
        this.metricBuilderFactory = metricBuilderFactory;
    }


    public synchronized GoTimer getMethodTimer(String destinationService,
                                               String destinationMethod) {
        if (StringUtils.isBlank(destinationService)) {
            destinationService = UNKNOWN;
        }
        if (StringUtils.isBlank(destinationMethod)) {
            destinationMethod = UNKNOWN;
        }
        return metricBuilderFactory.newMetric("client_rpc").
                withTag("method", UNKNOWN).
                withTag("destination_service", destinationService).
                withTag("destination_method", destinationMethod).buildTimer();
    }

}
