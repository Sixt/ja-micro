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

package com.sixt.service.framework.servicetest.service;

import com.google.gson.JsonObject;
import com.google.protobuf.Message;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.RpcCallException;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public interface ServiceUnderTest {

    /**
     * Send a request to the service under test.
     *
     * @param serviceMethod The name of the service method to call. E.g. "Journey.ReserveVehicle"
     * @param request       The request message
     * @return The response message
     */
    Message sendRequest(String serviceMethod, Message request) throws RpcCallException;

    Message sendRequest(String serviceMethod, Message request, OrangeContext orangeContext) throws RpcCallException;

    /**
     * Send an HTTP GET request to the service under test
     */
    String sendHttpGet(String path) throws Exception;

    /**
     * Send an HTTP PUT request to the service under test
     */
    String sendHttpPut(String path, String data) throws Exception;

    /**
     * Send an HTTP DELETE request to the service under test
     */
    String sendHttpDelete(String path) throws Exception;

    /**
     * Send an HTTP DELETE request to the service under test (with data)
     */
    String sendHttpDelete(String path, String data) throws Exception;

    /**
     * Send an HTTP POST request to the service under test
     */
    String sendHttpPost(String path, String data) throws Exception;

    /**
     * Get the expected events from Kafka for verification.
     *
     * @param expectedEvents A map of event name to event class. E.g. "JourneyStarted" ->
     *                       "JourneyStartedOuterClass.JourneyStarted.class"
     * @return A map of found events (event name to event message)
     */
    Map<String, Message> getExpectedEvents(Map<String, Class> expectedEvents);

    <TYPE extends Message> List<TYPE> getEventsOfType(String eventName, Class<TYPE> eventClass);

    /**
     * Get a list of events from Kafka by the given event type class.
     *
     * @param eventClass The implementation of the {@link Message} interface e.g. VehicleUnlocked.class
     * @param <TYPE> The type of the given event class.
     * @return A list of events by the given type class.
     */
    <TYPE extends Message> List<TYPE> getEventsOfType(Class<TYPE> eventClass);

    List<JsonObject> getAllJsonEvents();

    /**
     *
     * @param eventName The Kafka event name e.g. VehicleLocked
     * @param eventClass The implementation of the {@link Message} interface e.g. VehicleLocked.class
     * @param predicate Represents a condition of the event selection e.g. (VehicleLocked e) -> e.getVehicleId().equals(VEHICLE_ID)
     * @param timeout The time in milliseconds as a waiting period within the event could be found
     * @param <T> The type of the input for the predicate
     * @return The event of the type <T> or null if the event is not found
     */
    <T>T getEvent(String eventName, Class eventClass, Predicate<T> predicate, long timeout);

    /**
     * Clear the events read from kafka.
     */
    void clearReadEvents();

    void setServiceMethodTimeout(String serviceMethod, int timeout);

    void setDefaultRpcClientRetries(int count);

    /**
     * Set default timeout for calls to the service under test (in milliseconds)
     */
    void setDefaultRpcClientTimeout(int timeout);

    LoadBalancer getLoadBalancer();

    void shutdown();
}
