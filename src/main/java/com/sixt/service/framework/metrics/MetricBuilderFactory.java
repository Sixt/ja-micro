package com.sixt.service.framework.metrics;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class MetricBuilderFactory {
    private final Provider<MetricBuilder> metricBuilderProvider;

    @Inject
    public MetricBuilderFactory(Provider<MetricBuilder> metricBuilderProvider) {
        this.metricBuilderProvider = metricBuilderProvider;
    }

    public MetricBuilder newMetric(String baseName) {
        MetricBuilder metricBuilder = metricBuilderProvider.get();
        metricBuilder.setBaseName(baseName);
        return metricBuilder;
    }
}
