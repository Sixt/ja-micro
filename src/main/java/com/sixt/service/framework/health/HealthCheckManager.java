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

package com.sixt.service.framework.health;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.metrics.GoGauge;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import com.sixt.service.framework.registry.consul.RegistrationManager;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.sixt.service.framework.FeatureFlags.DEFAULT_HEALTH_CHECK_POLL_INTERVAL;
import static com.sixt.service.framework.FeatureFlags.HEALTH_CHECK_POLL_INTERVAL;

@Singleton
public class HealthCheckManager implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckManager.class);

    private final int pollTime; //in seconds
    private final MetricBuilderFactory metricBuilderFactory;
    private final RegistrationManager registrationManager;
    private final String serviceId;
    private final ServiceProperties serviceProps;
    private final HttpClient httpClient;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private ScheduledExecutorService executorService;
    protected Deque<HealthCheckContributor> pollingContributors = new ConcurrentLinkedDeque<>();
    protected Deque<HealthCheckContributor> oneShotContributors = new ConcurrentLinkedDeque<>();
    protected int failingChecksGauge;


    @Inject
    public HealthCheckManager(MetricBuilderFactory metricBuilderFactory,
                              RegistrationManager registrationManager,
                              ServiceProperties serviceProps,
                              HttpClient httpClient) {
        this.metricBuilderFactory = metricBuilderFactory;
        this.registrationManager = registrationManager;
        this.serviceProps = serviceProps;
        this.serviceId = serviceProps.getServiceInstanceId();
        this.httpClient = httpClient;
        pollTime = serviceProps.getIntegerProperty(HEALTH_CHECK_POLL_INTERVAL, DEFAULT_HEALTH_CHECK_POLL_INTERVAL);
    }

    public void registerPollingContributor(HealthCheckContributor contrib) {
        pollingContributors.add(contrib);
    }

    /**
     * A One-shot contributor is one that is removed from the list of contributors
     * as soon as it is healthy again.
     */
    public void registerOneShotContributor(HealthCheckContributor contrib) {
        oneShotContributors.add(contrib);
        reportCurrentStatus();
    }

    public void initialize() {
        GoGauge gauge = metricBuilderFactory.newMetric("health_checks").buildGauge();
        gauge.register("failing", () -> failingChecksGauge);
        executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(this, 0, pollTime, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        reportCurrentStatus();
    }

    private void reportCurrentStatus() {
        try {
            if (registrationManager.isRegistered()) {
                Collection<HealthCheck> healthChecks = getCurrentHealthChecks();
                HealthCheck.Status summary = getSummaryFor(healthChecks);
                updateHealthStatus(summary);
            } else {
                logger.info("Waiting for {} to be registered before updating health"
                        , serviceProps.getServiceName());
            }
        } catch (Exception ex) {
            if (!isShutdown.get()) {
                logger.warn("Caught exception updating health status", ex);
            }
        }
    }

    //TODO: specific to consul; need to refactor
    public void updateHealthStatus(HealthCheck.Status status) throws Exception {
        logger.trace("Updating health of {}", serviceProps.getServiceName());
        ContentResponse httpResponse = httpClient.newRequest(getHealthCheckUri(status)).method(HttpMethod.PUT).send();
        if (httpResponse.getStatus() != 200) {
            logger.warn("Received {} trying to update health", httpResponse.getStatus());
        }
    }

    private String getHealthCheckUri(HealthCheck.Status status) {
        StringBuilder sb = new StringBuilder("http://").append(serviceProps.getRegistryServer()).
                append("/v1/agent/check/").append(status.getConsulStatus()).append("/service:").
                append(serviceId);
        return sb.toString();
    }

    public Collection<HealthCheck> getCurrentHealthChecks() {
        int failCount = 0;
        List<HealthCheck> retval = new ArrayList<>();
        Iterator<HealthCheckContributor> iter = pollingContributors.iterator();
        while (iter.hasNext()) {
            HealthCheckContributor contributor = iter.next();
            HealthCheck hc = contributor.getHealthCheck();
            if (hc != null) {
                retval.add(hc);
                if (! hc.isStatus(HealthCheck.Status.PASS)) {
                    failCount++;
                }
            }
        }
        iter = oneShotContributors.iterator();
        while (iter.hasNext()) {
            HealthCheckContributor contributor = iter.next();
            HealthCheck hc = contributor.getHealthCheck();
            if (hc != null) {
                retval.add(hc);
                if (HealthCheck.Status.PASS.equals(hc.getStatus())) {
                    iter.remove();
                } else {
                    failCount++;
                }
            }
        }
        failingChecksGauge = failCount;
        return retval;
    }

    /**
     * We return the most severe of the statuses within the collection
     */
    public HealthCheck.Status getSummaryFor(Collection<HealthCheck> checks) {
        HealthCheck.Status retval = HealthCheck.Status.PASS;
        for (HealthCheck check : checks) {
            if (check.getStatus().moreSevereThan(retval)) {
                retval = check.getStatus();
            }
        }
        return retval;
    }

    public void shutdown() {
        isShutdown.set(true);
        executorService.shutdown();
    }
}
