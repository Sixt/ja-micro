package com.sixt.service.framework.kafka.messaging;

import com.google.protobuf.Parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TypeDictionary {

    // synchronized because put may be exected in different thread than read access
    // if synchronization is found too heavy for this, extract interface and implement an immutable dictionary and another modifyable one
    private final Map<MessageType, Parser> parsers = Collections.synchronizedMap(new HashMap<>());
    private final Map<MessageType, MessageHandler<? extends com.google.protobuf.Message>> handlers = Collections.synchronizedMap(new HashMap<>());


    public TypeDictionary() {

    }

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


    public MessageHandler<? extends com.google.protobuf.Message> putHandler(MessageType type, MessageHandler<? extends com.google.protobuf.Message> handler) {
        return handlers.put(type, handler);
    }

    public Parser putParser(MessageType type, Parser parser) {
        return parsers.put(type, parser);
    }

    public void putAllParsers(Map<MessageType, Parser> parsers) {
        this.parsers.putAll(parsers);
    }

    public void putAllHandlers(Map<MessageType, MessageHandler<? extends com.google.protobuf.Message>> handlers) {
        this.handlers.putAll(handlers);
    }

}
