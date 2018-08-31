package com.sixt.service.framework.kafka.messaging;


import com.google.inject.*;
import com.google.protobuf.Parser;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ReflectionTypeDictionaryFactoryTest {

    @Test
    public void testCreateFromClasspath() {
        // The ReflectionTypeDictionaryFactory is robust by design, so it does not throw any Exception.
        // Need to check the log output for the error test cases.


        // Dependency injection magic
        ServiceProperties serviceProperites = new ServiceProperties();

        Module[] modules = new Module[1];
        modules[0] = new TestInjectionModule(serviceProperites);

        Injector injector = Guice.createInjector(modules);

        ReflectionTypeDictionaryFactory rtdf = new ReflectionTypeDictionaryFactory(injector);
        TypeDictionary dictionary = rtdf.createFromClasspath();

        MessageHandler<? extends com.google.protobuf.Message> handler = dictionary.messageHandlerFor(MessageType.of(TypeDictionaryTest.class));
        assertNotNull(handler);

        MessageHandler<? extends com.google.protobuf.Message> unknownHandler = dictionary.messageHandlerFor(MessageType.of(TestMessageWithNoHandler.class));
        assertNull(unknownHandler);


        Parser parser = dictionary.parserFor((MessageType.of(TypeDictionaryTest.class)));
        assertNotNull(parser);

        assertNull(dictionary.parserFor(new MessageType("UnknownType")));
    }


    static class TestInjectionModule extends AbstractModule {
        private final ServiceProperties serviceProperties;

        TestInjectionModule(ServiceProperties serviceProperties) {
            this.serviceProperties = serviceProperties;
        }

        @Override
        protected void configure() {
            bind(ServiceProperties.class).toInstance(serviceProperties);
        }
    }



}


class HandlerOne implements MessageHandler<TypeDictionaryTest> {

    @Override
    public void onMessage(Message<TypeDictionaryTest> msg, OrangeContext context) {

    }
}

class HandlerTwo implements MessageHandler<TypeDictionaryTest> {

    @Inject
    public HandlerTwo(ServiceProperties properties) {

    }

    @Override
    public void onMessage(Message<TypeDictionaryTest> msg, OrangeContext context) {

    }
}

class UntypedHandler implements MessageHandler {

    @Override
    public void onMessage(Message msg, OrangeContext context) {

    }
}

class NoDefaultConstructorHandler implements MessageHandler<TypeDictionaryTest> {

    public NoDefaultConstructorHandler(int cruftlevel) {

    }

    @Override
    public void onMessage(Message<TypeDictionaryTest> msg, OrangeContext context) {

    }
}

class NonAccessibleDefaultConstructorHandler implements MessageHandler<TypeDictionaryTest> {

    private NonAccessibleDefaultConstructorHandler() {

    }

    @Override
    public void onMessage(Message<TypeDictionaryTest> msg, OrangeContext context) {

    }
}


