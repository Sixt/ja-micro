package com.sixt.service.framework.kafka.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Throw away any messages that caused the MessageHandler to throw an exception.
 *
 * It logs the offset of the message, so that a out-of-bounds mechanism may process the failed messages from the
 * original topic.
 */
public class DiscardFailedMessages implements FailedMessageProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DiscardFailedMessages.class);

    @Override
    public void onFailedMessage(Message failed, Throwable failureCause) {
        // TODO structured logging
        logger.warn("Discarding message",failureCause);
    }
}
