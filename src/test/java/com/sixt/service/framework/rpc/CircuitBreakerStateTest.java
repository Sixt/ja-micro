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

import org.junit.Test;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CircuitBreakerStateTest {

    @Test
    public void constructedHealthy() {
        CircuitBreakerState cb = new CircuitBreakerState(null);
        assertThat(cb.getState()).isEqualTo(CircuitBreakerState.State.PRIMARY_HEALTHY);
    }

    @Test
    public void testCanServeRequests() {
        CircuitBreakerState cb = new CircuitBreakerState(null);
        assertThat(cb.canServeRequests(false)).isTrue();
        cb.setState(CircuitBreakerState.State.SECONDARY_HEALTHY);
        assertThat(cb.canServeRequests(false)).isTrue();
        cb.setState(CircuitBreakerState.State.TERTIARY_HEALTHY);
        assertThat(cb.canServeRequests(false)).isTrue();
        cb.setState(CircuitBreakerState.State.PRIMARY_TRIPPED);
        assertThat(cb.canServeRequests(false)).isFalse();
        cb.setState(CircuitBreakerState.State.SECONDARY_TRIPPED);
        assertThat(cb.canServeRequests(false)).isFalse();
        cb.setState(CircuitBreakerState.State.TERTIARY_TRIPPED);
        assertThat(cb.canServeRequests(false)).isFalse();
        cb.setState(CircuitBreakerState.State.UNHEALTHY);
        assertThat(cb.canServeRequests(false)).isFalse();
        cb.setState(CircuitBreakerState.State.PRIMARY_PROBE);
        assertThat(cb.canServeRequests(false)).isTrue();
        cb.setState(CircuitBreakerState.State.SECONDARY_PROBE);
        assertThat(cb.canServeRequests(false)).isTrue();
        cb.setState(CircuitBreakerState.State.TERTIARY_PROBE);
        assertThat(cb.canServeRequests(false)).isTrue();
        cb.setState(CircuitBreakerState.State.PRIMARY_PROBE);
        assertThat(cb.canServeRequests(true)).isFalse();
        cb.setState(CircuitBreakerState.State.SECONDARY_PROBE);
        assertThat(cb.canServeRequests(true)).isFalse();
        cb.setState(CircuitBreakerState.State.TERTIARY_PROBE);
        assertThat(cb.canServeRequests(true)).isFalse();
    }

    @Test
    public void intermittentFailuresDontTrip() {
        CircuitBreakerState cb = new CircuitBreakerState(null);
        for (int i = 0; i < 10; i++) {
            assertThat(cb.canServeRequests(false)).isTrue();
            cb.requestComplete(true);
            assertThat(cb.canServeRequests(false)).isTrue();
            cb.requestComplete(false);
        }
        assertThat(cb.canServeRequests(false)).isTrue();
    }

    @Test
    public void testHasFailed() {
        CircuitBreakerState cb = new CircuitBreakerState(
                mock(ScheduledThreadPoolExecutor.class));
        for (int i = 0; i < CircuitBreakerState.HISTORY_SIZE - 1; i++) {
            cb.requestComplete(false);
            assertThat(cb.canServeRequests(false)).isTrue();
        }
        cb.requestComplete(false);
        assertThat(cb.canServeRequests(false)).isFalse();
    }

    //TODO: detailed state change tests
}
