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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;

public class GoCounter extends Counter implements Metric {

    //The structure of codahale metrics don't mesh with our go metrics.
    //To track failures and successes in different buckets requires using
    //  multiple underlying Counter objects;
    protected Counter successCounter;
    protected Counter failureCounter;
    protected String name;

    public GoCounter(String name) {
        this.name = name;
        successCounter = new Counter();
        failureCounter = new Counter();
    }

    public void incSuccess() {
        successCounter.inc();
    }

    public void incSuccess(long n) {
        successCounter.inc(n);
    }

    public void incFailure() {
        failureCounter.inc();
    }

    public void incFailure(long n) {
        failureCounter.inc(n);
    }

    public void decSuccess() {
        successCounter.dec();
    }

    public void decSuccess(long n) {
        successCounter.dec(n);
    }

    public void decFailure() {
        failureCounter.dec();
    }

    public void decFailure(long n) {
        failureCounter.dec(n);
    }

    public Counter getSuccessCounter() {
        return successCounter;
    }

    public Counter getFailureCounter() {
        return failureCounter;
    }

    public long getSuccessCount() {
        return successCounter.getCount();
    }

    public long getFailureCount() {
        return failureCounter.getCount();
    }

    public String getName() {
        return name;
    }

    public void reset() {
        successCounter = new Counter();
        failureCounter = new Counter();
    }

}
