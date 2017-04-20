package com.sixt.service.framework.injection;

import com.google.inject.AbstractModule;
import com.sixt.service.framework.kafka.messaging.ConsumerFactory;

public class MessagingModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ConsumerFactory.class).toProvider(ConsumerFactoryProvider.class);
    }
}
