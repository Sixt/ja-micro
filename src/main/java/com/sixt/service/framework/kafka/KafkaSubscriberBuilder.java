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

package com.sixt.service.framework.kafka;

import com.sixt.service.framework.metrics.MetricBuilderFactory;

import java.util.UUID;

public class KafkaSubscriberBuilder<TYPE> {

    protected KafkaSubscriberFactory<TYPE> parentFactory;
    protected EventReceivedCallback<TYPE> callback;
    protected String topic;
    protected String groupId = UUID.randomUUID().toString();
    protected boolean enableAutoCommit = false;
    protected KafkaSubscriber.OffsetReset offsetReset = KafkaSubscriber.OffsetReset.Earliest;
    protected int minThreads = 1;
    protected int maxThreads = 1;
    protected int idleTimeoutSeconds = 15;
    protected int pollTime = 1000;
    protected int throttleLimit = 100;
    private MetricBuilderFactory metricBuilderFactory;

    KafkaSubscriberBuilder(KafkaSubscriberFactory<TYPE> factory, String topic,
                           EventReceivedCallback<TYPE> callback) {
        this.parentFactory = factory;
        this.topic = topic;
        this.callback = callback;
    }

    /**
     * Unless called, will initialize with test_service UUID group-id
     */
    public KafkaSubscriberBuilder<TYPE> withGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * Unless called, will initialize without auto-commit
     */
    public KafkaSubscriberBuilder<TYPE> withAutoCommit(boolean value) {
        this.enableAutoCommit = value;
        return this;
    }

    /**
     * Unless called, will initialize by starting at the earliest offset
     */
    public KafkaSubscriberBuilder<TYPE> withOffsetReset(KafkaSubscriber.OffsetReset value) {
        this.offsetReset = value;
        return this;
    }

    /**
     * Unless called, will initialize with single-threaded reader
     */
    public KafkaSubscriberBuilder<TYPE> withThreadPool(int minThreads, int maxThreads, int idleTimeoutSeconds) {
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        return this;
    }

    public KafkaSubscriberBuilder<TYPE> withPollTime(int pollTimeMillis) {
        this.pollTime = pollTimeMillis;
        return this;
    }

    /**
     * Sets the limit at which throttling occurs, which is pausing the consumption from
     * kafka until the actual consumer can catch up.  Setting to -1 disables throttling
     * (which can cause unexpected memory bloat).
     */
    public KafkaSubscriberBuilder<TYPE> withThrottlingLimit(int throttleLimit) {
        this.throttleLimit = throttleLimit;
        return this;
    }

    public KafkaSubscriber<TYPE> build() {
        KafkaSubscriber<TYPE> retval = new KafkaSubscriber<>(callback, topic, groupId,
                enableAutoCommit, offsetReset, minThreads, maxThreads, idleTimeoutSeconds,
                pollTime, throttleLimit);
        retval.setMetricBuilderFactory(metricBuilderFactory);
        parentFactory.builtSubscriber(retval);
        return retval;
    }

    public void setMetricBuilderFactory(MetricBuilderFactory metricBuilderFactory) {
        this.metricBuilderFactory = metricBuilderFactory;
    }
}
