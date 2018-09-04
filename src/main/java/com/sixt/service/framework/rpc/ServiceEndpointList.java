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

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServiceEndpointList {

    private static final Logger logger = LoggerFactory.getLogger(ServiceEndpointList.class);

    protected volatile int size = 0;
    protected ServiceEndpointNode returnNext = null;
    protected ReentrantReadWriteLock mutex = new ReentrantReadWriteLock();

    public void add(ServiceEndpoint sep) {
        mutex.writeLock().lock();
        try {
            if (size == 0) {
                ServiceEndpointNode node = new ServiceEndpointNode(sep);
                this.returnNext = node;
                node.prev = node;
                node.next = node;
            } else {
                ServiceEndpointNode node = new ServiceEndpointNode(sep);
                ServiceEndpointNode previous = returnNext;
                returnNext = node;
                node.next = previous.next;
                node.next.prev = node;
                node.prev = previous;
                previous.next = node;
            }
            size++;
        } finally {
            mutex.writeLock().unlock();
        }
    }

    public ServiceEndpoint nextAvailable() {
        mutex.writeLock().lock(); //needs write b/c it calls canServeRequests
        try {
            ServiceEndpointNode retval = returnNext;
            for (int i = 0; i < size; i++) {
                if (retval.value.canServeRequests()) {
                    retval.value.incrementServingRequests();
                    returnNext = retval.next;
                    return retval.value;
                } else {
                    returnNext = returnNext.next;
                    retval = returnNext;
                }
            }
            //if we got here, there are none available
            return null;
        } finally {
            mutex.writeLock().unlock();
        }
    }

    public boolean isEmpty() {
        mutex.readLock().lock();
        try {
            return size == 0;
        } finally {
            mutex.readLock().unlock();
        }
    }

    public int size() {
        mutex.readLock().lock();
        try {
            return size;
        } finally {
            mutex.readLock().unlock();
        }
    }

    public void updateEndpointHealth(ServiceEndpoint ep, CircuitBreakerState.State state) {
        mutex.readLock().lock();
        try {
            ServiceEndpointNode current = returnNext;
            int count = size;
            for (int i = 0; i < count; i++) {
                if (current.value.getHostAndPort().equals(ep.getHostAndPort())) {
                    current.value.setCircuitBreakerState(state);
                    return;
                }
                current = current.next;
            }
            logger.error("updateEndpointHealth: endpoint not found: {}", ep.toString());
        } finally {
            mutex.readLock().unlock();
        }
    }

    //intended only for debugging
    public void debugDump(StringBuilder sb) {
        mutex.writeLock().lock();
        try {
            ServiceEndpointNode current = returnNext;
            for (int i = 0; i < size; i++) {
                sb.append("    ").append(current.toString()).append(": ").
                        append(current.value.getCircuitBreakerState()).append("\n");
                current = current.next;
            }
        } finally {
            mutex.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("size", size)
                .add("returnNext", returnNext)
                .toString();
    }
}

class ServiceEndpointNode {
    ServiceEndpoint value;
    ServiceEndpointNode prev;
    ServiceEndpointNode next;

    public ServiceEndpointNode(ServiceEndpoint value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
