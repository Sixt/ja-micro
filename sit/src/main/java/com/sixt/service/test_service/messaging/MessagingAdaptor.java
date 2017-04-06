package com.sixt.service.test_service.messaging;


import com.google.inject.Inject;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.messaging.*;
import com.sixt.service.test_service.api.Echo;
import com.sixt.service.test_service.api.Greeting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagingAdaptor {
    private static final Logger logger = LoggerFactory.getLogger(MessagingAdaptor.class);

    // Nota bene: Guice can only inject into static inner classes.
    public static class GreetingHandler implements MessageHandler<Greeting> {
        private final Producer producer;

        @Inject
        public GreetingHandler(ProducerFactory producerFactory) {
            producer = producerFactory.createProducer();
        }

        @Override
        public void onMessage(Message<Greeting> message, OrangeContext context) {
            logger.info("Received {}", message);

            String greeting = message.getPayload().getGreeting();

            Echo reply = Echo.newBuilder().setEcho(greeting).build();

            Message replyMessage = Messages.replyTo(message, reply, context);
            producer.send(replyMessage);
        }
    }

    public static class EchoHandler implements MessageHandler<Echo> {
        @Override
        public void onMessage(Message<Echo> message, OrangeContext context) {
            logger.info("Received {}", message);
        }
    }


}