package com.sixt.service.framework.kafka.messaging;

import com.google.protobuf.Parser;

import java.util.HashMap;
import java.util.Map;

public class TypeDictionary {

    private final Map<MessageType, Parser> parsers = new HashMap<>();
    private final Map<MessageType, MessageHandler<? extends com.google.protobuf.Message>> handlers = new HashMap<>();

    public TypeDictionary(Map<MessageType, MessageHandler<? extends com.google.protobuf.Message>> handlers, Map<MessageType, Parser> parsers) {
        this.handlers.putAll(handlers);
        this.parsers.putAll(parsers);
    }

    /**
     * @param type
     * @return null if no MessageHandler was found for the type, otherwise the handler
     */
    public MessageHandler<? extends com.google.protobuf.Message> messageHandlerFor(MessageType type) {
        return handlers.get(type);
    }

    /**
     * @param type
     * @return null if no Parser was found for the type, otherwise the parser
     */
    public Parser parserFor(MessageType type) {
        return parsers.get(type);
    }
}
