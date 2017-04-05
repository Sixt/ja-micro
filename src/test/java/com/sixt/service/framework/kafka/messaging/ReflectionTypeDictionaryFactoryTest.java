package com.sixt.service.framework.kafka.messaging;


import com.google.protobuf.Parser;
import com.sixt.service.framework.OrangeContext;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ReflectionTypeDictionaryFactoryTest {

    @Test
    public void populateDictionaryWithHandlers() {

        // The ReflectionTypeDictionaryFactory is robust by design, so it does not throw any Exception.
        // Need to check the log output for the error test cases.

        ReflectionTypeDictionaryFactory rtdf = new ReflectionTypeDictionaryFactory();
        TypeDictionary dictionary = rtdf.createTypeDictionaryFromClasspath();


        MessageHandler<? extends com.google.protobuf.Message> handler = dictionary.messageHandlerFor(MessageType.of(TypeDictionaryTest.class));
        assertNotNull(handler);

        MessageHandler<? extends com.google.protobuf.Message> unknownHandler = dictionary.messageHandlerFor(MessageType.of(TestMessageWithNoHandler.class));
        assertNull(unknownHandler);
    }


    @Test
    public void populateDictionaryWithParsers() {

        // The ReflectionTypeDictionaryFactory is robust by design, so it does not throw any Exception.
        // Need to check the log output for the error test cases.

        ReflectionTypeDictionaryFactory rtdf = new ReflectionTypeDictionaryFactory();
        TypeDictionary dictionary = rtdf.createTypeDictionaryFromClasspath();

        Parser parser = dictionary.parserFor((MessageType.of(TypeDictionaryTest.class)));
        assertNotNull(parser);

        assertNull(dictionary.parserFor(new MessageType("UnknownType")));
    }

}


class HandlerOne implements MessageHandler<TypeDictionaryTest> {

    @Override
    public void onMessage(Message<TypeDictionaryTest> msg, OrangeContext context) {

    }
}

class HandlerTwo implements MessageHandler<TypeDictionaryTest> {

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
