package com.sixt.service.test_service.handler;


import com.google.inject.Inject;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.messaging.*;
import com.sixt.service.test_service.api.Echo;
import com.sixt.service.test_service.api.Greeting;

public class RequestReplyMessageHandler implements MessageHandler<Greeting> {

    private final Producer producer;

    @Inject
    public RequestReplyMessageHandler(Producer sender) {
        this.producer = sender;
    }

    @Override
    public void onMessage(Message<Greeting> command, OrangeContext context) {
        Greeting greeting = command.getPayload();

        Echo echo = Echo.newBuilder().setEcho(greeting.getGreeting()).build();

        Message response = Messages.replyTo(command).with(echo);

        producer.send(response, context);


        // A new request
        Greeting hello = Greeting.newBuilder().setGreeting("Hello").build();

        Topic target = new Topic("test");
        Message sayHello = Messages.requestFor(target).with(hello);

    }

}
