package com.sixt.service.framework.kafka.messaging;

/**
 * Created by abjb on 4/4/17.
 */
public class UnknownMessageHandlerException extends Exception {
    public UnknownMessageHandlerException(MessageType type) {
        super(type.toString());
    }
}
