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

package com.sixt.service.framework.rpc;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.sixt.service.framework.rpc.CircuitBreakerState.*;
import static com.sixt.service.framework.rpc.CircuitBreakerState.State.*;
import static org.assertj.core.api.Assertions.assertThat;

public class CircuitBreakerStateIntegrationTest {

    private CircuitBreakerState breaker;
    private StubExecutor executor;

    @Before
    public void setup() {
        executor = new StubExecutor(2);
        breaker = new CircuitBreakerState(executor);
    }

    @Test
    public void toHellAndBack() {
        for (int i = 0; i < 3; i++) {
            assertThat(breaker.getState()).isEqualTo(PRIMARY_HEALTHY);
            breaker.requestComplete(false);
            breaker.requestComplete(false);
            assertThat(breaker.getState()).isEqualTo(PRIMARY_HEALTHY);
            breaker.requestComplete(false);
            assertThat(breaker.getState()).isEqualTo(PRIMARY_TRIPPED);
            executor.tick(PRIMARY_TRIP_TIME - 1);
            assertThat(breaker.getState()).isEqualTo(PRIMARY_TRIPPED);
            executor.tick(1);
            assertThat(breaker.getState()).isEqualTo(PRIMARY_PROBE);
            breaker.requestComplete(false);
            assertThat(breaker.getState()).isEqualTo(SECONDARY_TRIPPED);
            executor.tick(SECONDARY_TRIP_TIME - 1);
            assertThat(breaker.getState()).isEqualTo(SECONDARY_TRIPPED);
            executor.tick(1);
            assertThat(breaker.getState()).isEqualTo(SECONDARY_PROBE);
            breaker.requestComplete(false);
            assertThat(breaker.getState()).isEqualTo(TERTIARY_TRIPPED);
            executor.tick(TERTIARY_TRIP_TIME - 1);
            assertThat(breaker.getState()).isEqualTo(TERTIARY_TRIPPED);
            executor.tick(1);
            assertThat(breaker.getState()).isEqualTo(TERTIARY_PROBE);
            breaker.requestComplete(false);
            assertThat(breaker.getState()).isEqualTo(TERTIARY_TRIPPED);
            executor.tick(TERTIARY_TRIP_TIME);
            assertThat(breaker.getState()).isEqualTo(TERTIARY_PROBE);
            breaker.requestComplete(true);
            assertThat(breaker.getState()).isEqualTo(TERTIARY_HEALTHY);
            executor.tick(TERTIARY_TRIP_TIME);
            assertThat(breaker.getState()).isEqualTo(SECONDARY_HEALTHY);
            executor.tick(SECONDARY_TRIP_TIME);
            assertThat(breaker.getState()).isEqualTo(PRIMARY_HEALTHY);
        }
    }

    @Test
    public void healthyToTripped() {
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(PRIMARY_TRIPPED);
        executor.tick(PRIMARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(PRIMARY_PROBE);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_TRIPPED);
        executor.tick(SECONDARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_PROBE);
        breaker.requestComplete(true);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_HEALTHY);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_TRIPPED);
        executor.tick(SECONDARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_PROBE);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(TERTIARY_TRIPPED);
        executor.tick(TERTIARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(TERTIARY_PROBE);
        breaker.requestComplete(true);
        assertThat(breaker.getState()).isEqualTo(TERTIARY_HEALTHY);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(TERTIARY_TRIPPED);
    }

    @Test
    public void primaryProbeToHealthy() {
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(PRIMARY_TRIPPED);
        executor.tick(PRIMARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(PRIMARY_PROBE);
        breaker.requestComplete(true);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_HEALTHY);
        executor.tick(PRIMARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(PRIMARY_HEALTHY);
    }

    @Test
    public void stateChangerRespectsFailures() {
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(PRIMARY_TRIPPED);
        executor.tick(PRIMARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(PRIMARY_PROBE);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_TRIPPED);
        executor.tick(SECONDARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_PROBE);
        breaker.requestComplete(true);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_HEALTHY);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        executor.tick(PRIMARY_TRIP_TIME);
        assertThat(executor.scheduledTasks).hasSize(2);
        executor.tick(PRIMARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(SECONDARY_PROBE);
        breaker.requestComplete(false);
        assertThat(breaker.getState()).isEqualTo(TERTIARY_TRIPPED);
        executor.tick(TERTIARY_TRIP_TIME);
        assertThat(breaker.getState()).isEqualTo(TERTIARY_PROBE);
        breaker.requestComplete(true);
        assertThat(breaker.getState()).isEqualTo(TERTIARY_HEALTHY);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        breaker.requestComplete(false);
        executor.tick(TERTIARY_TRIP_TIME);
    }

    private class StubExecutor extends ScheduledThreadPoolExecutor {

        private int currentTime = 0;
        private List<ScheduledTask> scheduledTasks = new LinkedList<>();

        public StubExecutor(int corePoolSize) {
            super(corePoolSize);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduledTasks.add(new ScheduledTask(currentTime + (int)delay, command));
            return null;
        }

        public void tick(int ticks) {
            for (int i = 0; i < ticks; i++) {
                currentTime++;
                while (!scheduledTasks.isEmpty() && scheduledTasks.get(0).startTime == currentTime) {
                    ScheduledTask task = scheduledTasks.remove(0);
                    task.command.run();
                }
            }
        }

        private class ScheduledTask {
            int startTime;
            Runnable command;

            ScheduledTask(int startTime, Runnable command) {
                this.startTime = startTime;
                this.command = command;
            }
        }
    }
}
