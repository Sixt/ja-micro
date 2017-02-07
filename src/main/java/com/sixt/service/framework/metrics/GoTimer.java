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

package com.sixt.service.framework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;

import java.util.concurrent.TimeUnit;

public class GoTimer extends Timer implements Metric {

    //The structure of codahale metrics don't mesh with our go metrics.
    //To track failures and successes in different buckets requires using
    //  multiple underlying Timer objects;
    protected Timer successTimer;
    protected Timer failureTimer;
    protected String name;

    public GoTimer(String name) {
        this.name = name;
        reset();
    }

    public long start() {
        return System.nanoTime();
    }

    public void recordSuccess(long startTime) {
        successTimer.update(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }

    public void recordFailure(long startTime) {
        failureTimer.update(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }

    public Timer getSuccessTimer() {
        return successTimer;
    }

    public Timer getFailureTimer() {
        return failureTimer;
    }

    public String getName() {
        return name;
    }

    public void reset() {
        successTimer = new Timer();
        failureTimer = new Timer();
    }

}
