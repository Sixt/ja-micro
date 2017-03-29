package com.sixt.service.test_service.handler;


import com.google.inject.Inject;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.messaging.KafkaMessagingProducer;
import com.sixt.service.framework.kafka.messaging.Message;
import com.sixt.service.framework.kafka.messaging.MessageHandler;
import com.sixt.service.test_service.api.Messages;

public class RequestReplyMessageHandler implements MessageHandler<Messages.Greeting> {

    private final KafkaMessagingProducer producer;

    @Inject
    public RequestReplyMessageHandler(KafkaMessagingProducer sender) {
        this.producer = sender;
    }

    @Override
    public void onMessage(Message<Messages.Greeting> command, OrangeContext context) {
        String greeting = command.getMessage().getGreeting();
        Messages.Echo echo = Messages.Echo.newBuilder().setEcho(greeting).build();

        Message response = Message.replyTo(command).with(echo);
        producer.send(response, context);
    }

}
