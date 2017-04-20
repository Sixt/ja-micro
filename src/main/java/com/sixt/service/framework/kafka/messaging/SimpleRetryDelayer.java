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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Delay with a fixed interval up to a maximum total delay.
 * <p>
 * The maximum total delay is simply the sum of all delays, but not the real time spend in delaying.
 */
public final class SimpleRetryDelayer implements RetryDelayer {

    private final long delayIntervallMillis;
    private final long maximumDelayMillis;

    private final AtomicLong accumulatedDelay = new AtomicLong(0);

    public SimpleRetryDelayer(long delayIntervallMillis, long maximumDelayMillis) {
        this.delayIntervallMillis = delayIntervallMillis;
        this.maximumDelayMillis = maximumDelayMillis;
    }


    @Override
    public boolean delay() {
        long total = accumulatedDelay.addAndGet(delayIntervallMillis);

        if (total >= maximumDelayMillis) {
            return false;
        }

        try {
            Thread.sleep(delayIntervallMillis);
        } catch (InterruptedException ignored) {

        }

        return true;
    }

    @Override
    public void reset() {
        accumulatedDelay.set(0);
    }
}
