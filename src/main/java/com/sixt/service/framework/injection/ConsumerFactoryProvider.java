package com.sixt.service.framework.injection;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.kafka.messaging.ConsumerFactory;
import com.sixt.service.framework.kafka.messaging.ReflectionTypeDictionaryFactory;
import com.sixt.service.framework.kafka.messaging.TypeDictionary;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import io.opentracing.Tracer;

public class ConsumerFactoryProvider implements Provider<ConsumerFactory> {

    private final Injector injector;
    private final ServiceProperties serviceProperties;
    private final Tracer tracer;
    private final MetricBuilderFactory metricBuilderFactory;

    private TypeDictionary typeDictionary = null;

    @Inject
    public ConsumerFactoryProvider(Injector injector, ServiceProperties serviceProperties, Tracer tracer, MetricBuilderFactory metricBuilderFactory) {
        this.injector = injector;
        this.serviceProperties = serviceProperties;
        this.tracer = tracer;
        this.metricBuilderFactory = metricBuilderFactory;
    }

    @Override
    public ConsumerFactory get() {
        if(typeDictionary == null) {
            // Thread safety: no need for volatile / synchronization - it doesn't hurt much if the TypeDictionary is created multiple times.
            ReflectionTypeDictionaryFactory  dictionaryFactory = new ReflectionTypeDictionaryFactory(injector);
            typeDictionary = dictionaryFactory.createFromClasspath();
        }

        return new ConsumerFactory(serviceProperties, typeDictionary, tracer, metricBuilderFactory);
    }
}

