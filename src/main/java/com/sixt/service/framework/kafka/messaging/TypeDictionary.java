package com.sixt.service.framework.kafka.messaging;

import com.google.protobuf.Parser;

public interface TypeDictionary {

    public MessageHandler messageHandlerFor(MessageType type);

    public Parser parserFor(MessageType type);
}
