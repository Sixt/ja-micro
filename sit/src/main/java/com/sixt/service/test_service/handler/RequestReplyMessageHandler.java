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


        Topic target = Topic.defaultServiceInbox("com.sixt.service.cruftlord");
        Topic trash = Topic.serviceInbox("com.sixt.service.cruftlord", "trash");

        Message response = Messages.replyTo(command, echo, context);

        //Message sayHello = Messages.requestFor(target, "a crufty key", echo, context);
        Message sayHelloAgain = Messages.requestFor(target, trash, "a crufty key", echo, context);

        Message fireAndForget = Messages.oneWayMessage(target, "another key", echo, context);


        producer.send(response);
    }

}
