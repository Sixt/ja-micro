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
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class CircuitBreakerStateTest {

    @Mock
    private ScheduledThreadPoolExecutor executor;
    private CircuitBreakerState breaker;

    @Before
    public void setup() {
        breaker = new CircuitBreakerState(executor);
    }

    @Test
    public void constructedHealthy() {
        assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.State.PRIMARY_HEALTHY);
    }

    @Test
    public void testCanServeRequests() {
        assertThat(breaker.canServeRequests(false)).isTrue();
        breaker.setState(CircuitBreakerState.State.SECONDARY_HEALTHY);
        assertThat(breaker.canServeRequests(false)).isTrue();
        breaker.setState(CircuitBreakerState.State.TERTIARY_HEALTHY);
        assertThat(breaker.canServeRequests(false)).isTrue();
        breaker.setState(CircuitBreakerState.State.PRIMARY_TRIPPED);
        assertThat(breaker.canServeRequests(false)).isFalse();
        breaker.setState(CircuitBreakerState.State.SECONDARY_TRIPPED);
        assertThat(breaker.canServeRequests(false)).isFalse();
        breaker.setState(CircuitBreakerState.State.TERTIARY_TRIPPED);
        assertThat(breaker.canServeRequests(false)).isFalse();
        breaker.setState(CircuitBreakerState.State.UNHEALTHY);
        assertThat(breaker.canServeRequests(false)).isFalse();
        breaker.setState(CircuitBreakerState.State.PRIMARY_PROBE);
        assertThat(breaker.canServeRequests(false)).isTrue();
        breaker.setState(CircuitBreakerState.State.SECONDARY_PROBE);
        assertThat(breaker.canServeRequests(false)).isTrue();
        breaker.setState(CircuitBreakerState.State.TERTIARY_PROBE);
        assertThat(breaker.canServeRequests(false)).isTrue();
        breaker.setState(CircuitBreakerState.State.PRIMARY_PROBE);
        assertThat(breaker.canServeRequests(true)).isFalse();
        breaker.setState(CircuitBreakerState.State.SECONDARY_PROBE);
        assertThat(breaker.canServeRequests(true)).isFalse();
        breaker.setState(CircuitBreakerState.State.TERTIARY_PROBE);
        assertThat(breaker.canServeRequests(true)).isFalse();
    }

    @Test
    public void intermittentFailuresDontTrip() {
        for (int i = 0; i < 10; i++) {
            assertThat(breaker.canServeRequests(false)).isTrue();
            breaker.requestComplete(true);
            assertThat(breaker.canServeRequests(false)).isTrue();
            breaker.requestComplete(false);
        }
        assertThat(breaker.canServeRequests(false)).isTrue();
    }

    @Test
    public void testHasFailed() {
        for (int i = 0; i < CircuitBreakerState.HISTORY_SIZE - 1; i++) {
            breaker.requestComplete(false);
            assertThat(breaker.canServeRequests(false)).isTrue();
        }
        breaker.requestComplete(false);
        assertThat(breaker.canServeRequests(false)).isFalse();
    }

    @Test
    public void timerCheckingHealthyStateDoesntWarn() {
        //if multiple calls take a breaker to the tripped state, there will be
        //multiple timer callbacks.  based on timing, it's possible for a breaker
        //to already be healthy when the timer fires.  don't log in that case
        breaker.primaryTrippedToPrimaryProbe.run();
    }

}
