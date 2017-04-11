package com.sixt.service.framework.kafka.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discard any messages that caused the MessageHandler to throw an exception.
 *
 * It logs topic and offset of the message, so a out-of-bounds mechanism can process / re-try any failed messages.
 *
 */
public class DiscardFailedMessages implements FailedMessageProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DiscardFailedMessages.class);

    @Override
    public boolean onFailedMessage(Message failed, Throwable failureCause) {
        logger.warn(failed.getMetadata().getLoggingMarker(), "Discarded failing message.",failureCause);

        return false;
    }
}
