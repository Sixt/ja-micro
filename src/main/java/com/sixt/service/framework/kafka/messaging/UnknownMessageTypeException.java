package com.sixt.service.framework.kafka.messaging;

/**
 * Created by abjb on 4/4/17.
 */
public class UnknownMessageTypeException extends Exception {
    public UnknownMessageTypeException(MessageType type) {
        super(type.toString());
    }
}
