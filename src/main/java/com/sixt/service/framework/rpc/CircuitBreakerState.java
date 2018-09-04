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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.sixt.service.framework.rpc.CircuitBreakerState.State.*;

public class CircuitBreakerState {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerState.class);

    public final static int HISTORY_SIZE = 3;
    public final static int PRIMARY_TRIP_TIME = 15;
    public final static int SECONDARY_TRIP_TIME = 30;
    public final static int TERTIARY_TRIP_TIME = 60;

    private ScheduledThreadPoolExecutor executor;

    public enum State {
        PRIMARY_HEALTHY,
        PRIMARY_TRIPPED,
        PRIMARY_PROBE,
        SECONDARY_HEALTHY,
        SECONDARY_TRIPPED,
        SECONDARY_PROBE,
        TERTIARY_HEALTHY,
        TERTIARY_TRIPPED,
        TERTIARY_PROBE,
        UNHEALTHY
    }

    private volatile State state;
    private List<Boolean> responseHistory = new LinkedList<>();

    public CircuitBreakerState(ScheduledThreadPoolExecutor executor) {
        this.executor = executor;
        this.state = PRIMARY_HEALTHY;
    }

    public synchronized void setState(State state) {
        this.state = state;
    }

    public State getState() {
        return state;
    }

    public synchronized boolean canServeRequests(boolean oneAlreadyQueued) {
        switch (state) {
            case PRIMARY_HEALTHY:
            case SECONDARY_HEALTHY:
            case TERTIARY_HEALTHY:
                return true;
            case PRIMARY_TRIPPED:
            case SECONDARY_TRIPPED:
            case TERTIARY_TRIPPED:
            case UNHEALTHY:
                return false;
            case PRIMARY_PROBE:
            case SECONDARY_PROBE:
            case TERTIARY_PROBE:
                return ! oneAlreadyQueued;
            default:
                throw new IllegalStateException("Unexpected state: " + state);
        }
    }

    public synchronized void requestComplete(boolean resultGood) {
        switch (state) {
            case PRIMARY_HEALTHY:
            case SECONDARY_HEALTHY:
            case TERTIARY_HEALTHY:
                responseHistory.add(resultGood);
                if (responseHistory.size() > HISTORY_SIZE) {
                    responseHistory.remove(0);
                }
                if (hasFailed()) {
                    logger.debug("CircuitBreaker tripped!");
                    if (state.equals(PRIMARY_HEALTHY)) {
                        setState(PRIMARY_TRIPPED);
                        executor.schedule(primaryTrippedToPrimaryProbe,
                                PRIMARY_TRIP_TIME, TimeUnit.SECONDS);
                    } else if (state.equals(SECONDARY_HEALTHY)) {
                        setState(SECONDARY_TRIPPED);
                        executor.schedule(secondaryTrippedToSecondaryProbe,
                                SECONDARY_TRIP_TIME, TimeUnit.SECONDS);
                    } else {
                        setState(TERTIARY_TRIPPED);
                        executor.schedule(tertiaryTrippedToTertiaryProbe,
                                TERTIARY_TRIP_TIME, TimeUnit.SECONDS);
                    }
                }
                break;
            case PRIMARY_TRIPPED:
            case SECONDARY_TRIPPED:
            case TERTIARY_TRIPPED:
            case UNHEALTHY:
                return;
            case PRIMARY_PROBE:
                if (resultGood) {
                    logger.debug("CircuitBreaker was tripped, became healthy");
                    setState(SECONDARY_HEALTHY);
                    responseHistory.clear();
                    executor.schedule(secondaryHealthyToPrimaryHealthy,
                            PRIMARY_TRIP_TIME, TimeUnit.SECONDS);
                } else {
                    logger.debug("CircuitBreaker tripped!");
                    setState(SECONDARY_TRIPPED);
                    executor.schedule(secondaryTrippedToSecondaryProbe,
                            SECONDARY_TRIP_TIME, TimeUnit.SECONDS);
                }
                break;
            case SECONDARY_PROBE:
                if (resultGood) {
                    logger.debug("CircuitBreaker was tripped, became healthy");
                    setState(SECONDARY_HEALTHY);
                    responseHistory.clear();
                    executor.schedule(secondaryHealthyToPrimaryHealthy,
                            SECONDARY_TRIP_TIME, TimeUnit.SECONDS);
                } else {
                    logger.debug("CircuitBreaker tripped!");
                    setState(TERTIARY_TRIPPED);
                    executor.schedule(tertiaryTrippedToTertiaryProbe,
                            TERTIARY_TRIP_TIME, TimeUnit.SECONDS);
                }
                break;
            case TERTIARY_PROBE:
                if (resultGood) {
                    logger.debug("CircuitBreaker was tripped, became healthy");
                    setState(TERTIARY_HEALTHY);
                    responseHistory.clear();
                    executor.schedule(tertiaryHealthyToSecondaryHealthy,
                            TERTIARY_TRIP_TIME, TimeUnit.SECONDS);
                } else {
                    logger.debug("CircuitBreaker tripped!");
                    setState(TERTIARY_TRIPPED);
                    executor.schedule(tertiaryTrippedToTertiaryProbe,
                            TERTIARY_TRIP_TIME, TimeUnit.SECONDS);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected state: " + state);
        }
    }

    public static boolean isTripped(State state) {
        return state.equals(SECONDARY_TRIPPED) || state.equals(TERTIARY_TRIPPED) ||
                state.equals(UNHEALTHY);
    }

    private boolean hasFailed() {
        int size = responseHistory.size();
        if (size < HISTORY_SIZE) {
            return false;
        } else if (size > HISTORY_SIZE) {
            logger.warn("Unexpected responseHistory size: {}", size);
            return false;
        } else {
            int occurrences = Collections.frequency(responseHistory, Boolean.FALSE);
            return occurrences >= HISTORY_SIZE;
        }
    }

    protected class StateChanger implements Runnable {
        private State fromState;
        private State toState;

        public StateChanger(State fromState, State toState) {
            this.fromState = fromState;
            this.toState = toState;
        }

        @Override
        public void run() {
            try {
                synchronized (CircuitBreakerState.this) {
                    if (CircuitBreakerState.this.getState().equals(fromState)) {
                        logger.debug("CircuitBreaker timer elapsed -> {}", toState);
                        CircuitBreakerState.this.setState(toState);
                        if (toState.equals(SECONDARY_HEALTHY)) {
                            executor.schedule(secondaryHealthyToPrimaryHealthy,
                                    SECONDARY_TRIP_TIME, TimeUnit.SECONDS);
                        }
                    } else if (isHealthy(fromState) &&
                            isTripped(CircuitBreakerState.this.getState())) {
                        // don't warn, because this could happen.
                        logger.trace("Not changing {} to {}, is tripped", fromState, toState);
                    } else if (CircuitBreakerState.this.getState().equals(PRIMARY_HEALTHY) &&
                            fromState.equals(PRIMARY_TRIPPED) && toState.equals(PRIMARY_PROBE)) {
                        //do nothing, this can happen
                    } else if (CircuitBreakerState.this.getState().equals(SECONDARY_HEALTHY) &&
                            fromState.equals(SECONDARY_TRIPPED) && toState.equals(SECONDARY_PROBE)) {
                        //do nothing, this can happen
                    } else if (CircuitBreakerState.this.getState().equals(TERTIARY_HEALTHY) &&
                            fromState.equals(TERTIARY_TRIPPED) && toState.equals(TERTIARY_PROBE)) {
                        //do nothing, this can happen
                    } else {
                        //multiple failures to a service can result in multiple timers being created,
                        //and then when one of those timers fire, another timer could have changed the
                        //fromState already.  we haven't seen any actual bugs in this code, but
                        //it would also be possible to refactor the timers so these else cases
                        //wouldn't happen
                        logger.debug("CircuitBreaker wasn't in state {}" +
                                ", so not changing it to {}. Current state = {}",
                                fromState, toState, getState());
                    }
                }
            } catch (Exception ex) {
                logger.error("Caught exception changing state", ex);
            }
        }

        private boolean isHealthy(State state) {
            return state.equals(SECONDARY_HEALTHY) ||
                    state.equals(TERTIARY_HEALTHY);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("state", state)
                .add("responseHistory", responseHistory)
                .toString();
    }

    @VisibleForTesting
    protected StateChanger primaryTrippedToPrimaryProbe = new StateChanger(
            State.PRIMARY_TRIPPED, State.PRIMARY_PROBE);
    protected StateChanger secondaryHealthyToPrimaryHealthy = new StateChanger(
            State.SECONDARY_HEALTHY, State.PRIMARY_HEALTHY);
    protected StateChanger secondaryTrippedToSecondaryProbe = new StateChanger(
            State.SECONDARY_TRIPPED, State.SECONDARY_PROBE);
    protected StateChanger tertiaryTrippedToTertiaryProbe = new StateChanger(
            State.TERTIARY_TRIPPED, State.TERTIARY_PROBE);
    protected StateChanger tertiaryHealthyToSecondaryHealthy = new StateChanger(
            State.TERTIARY_HEALTHY, State.SECONDARY_HEALTHY);
}
