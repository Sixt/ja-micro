package com.sixt.service.framework.rpc;

import com.sixt.service.framework.annotation.HealthCheckProvider;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.health.HealthCheckContributor;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@HealthCheckProvider
@Singleton
public class ServiceDependencyHealthCheck implements HealthCheckContributor {

    private ConcurrentLinkedQueue<ServiceEndpoint> endpoints = new ConcurrentLinkedQueue<>();

    @Override
    public HealthCheck getHealthCheck() {
        List<ServiceEndpoint> trippedBreakers = new ArrayList<>();
        trippedBreakers.forEach(ep -> {
            if (CircuitBreakerState.isTripped(ep.getCircuitBreakerState())) {
                trippedBreakers.add(ep);
            }
        });
        if (trippedBreakers.isEmpty()) {
            return new HealthCheck();
        } else {
            return buildFailedHealthCheck(trippedBreakers);
        }
    }

    // It is unknown whether the dependency is desired or required, so we set to WARN
    private HealthCheck buildFailedHealthCheck(List<ServiceEndpoint> trippedBreakers) {
        StringBuilder sb = new StringBuilder();
        trippedBreakers.forEach(serviceEndpoint -> {
            sb.append(serviceEndpoint.getServiceName()).append('/')
                    .append(serviceEndpoint.getAvailZone()).append('/')
                    .append(serviceEndpoint.getHostAndPort()).append(' ');
        });
        return new HealthCheck("RPC dependencies", HealthCheck.Status.WARN, sb.toString());
    }

    @Override
    public boolean shouldRegister() {
        return true;
    }

    public void monitorServiceEndpoint(ServiceEndpoint serviceEndpoint) {
        endpoints.add(serviceEndpoint);
    }

}
