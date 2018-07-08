package com.sixt.service.framework.metrics;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import java.util.stream.IntStream;

public final class MetricBuilderTest {

    @Test
    public void testShouldVerifyNoDuplicateMetricRegistered() {
        final MetricRegistry metricRegistry = new MetricRegistry();

        final MetricBuilderFactory metricBuilderFactory = new MetricBuilderFactory(
                () -> new MetricBuilder(metricRegistry)
        );

        IntStream.rangeClosed(0, 500)
                .parallel()
                .forEach(i -> metricBuilderFactory.newMetric("metric_name").buildCounter().incSuccess());
    }
}