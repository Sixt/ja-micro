/**
 * Copyright 2016-2017 Sixt GmbH & Co. Autovermietung KG
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

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
