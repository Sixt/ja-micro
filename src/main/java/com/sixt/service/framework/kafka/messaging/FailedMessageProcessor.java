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


/**
 * A Strategy interface to allow exchangeable failure handling behaviour.
 */
public interface FailedMessageProcessor {

    /**
     * This method decides if a failed message should be re-tried or not.
     *
     * It may block the current thread (which is calling the message handler) if a delay between retries is required.
     *
     * @param failed the failed message
     * @param failureCause the root cause of the failure
     * @return true if message delivery should be re-tried, false otherwise
     */
    boolean onFailedMessage(Message failed, Throwable failureCause);

}
