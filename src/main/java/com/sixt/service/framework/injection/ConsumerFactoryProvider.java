package com.sixt.service.framework.injection;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.kafka.messaging.ConsumerFactory;
import com.sixt.service.framework.kafka.messaging.ReflectionTypeDictionaryFactory;
import com.sixt.service.framework.kafka.messaging.TypeDictionary;

public class ConsumerFactoryProvider implements Provider<ConsumerFactory> {

    private final Injector injector;
    private final ServiceProperties serviceProperties;

    @Inject
    public ConsumerFactoryProvider(Injector injector, ServiceProperties serviceProperties) {
        this.injector = injector;
        this.serviceProperties = serviceProperties;
    }

    @Override
    public ConsumerFactory get() {
        ReflectionTypeDictionaryFactory dictionaryFactory = new ReflectionTypeDictionaryFactory(injector);
        TypeDictionary typeDictionary = dictionaryFactory.createFromClasspath();

        return new ConsumerFactory(serviceProperties, typeDictionary);
    }


}

