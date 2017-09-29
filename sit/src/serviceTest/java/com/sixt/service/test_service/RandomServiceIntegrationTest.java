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

package com.sixt.service.test_service;

import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.kafka.KafkaPublisher;
import com.sixt.service.framework.kafka.KafkaPublisherFactory;
import com.sixt.service.framework.kafka.TopicMessageCounter;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.rpc.ServiceEndpoint;
import com.sixt.service.framework.servicetest.mockservice.CommandResponseMapping;
import com.sixt.service.test_service.api.TestServiceOuterClass;
import com.sixt.service.test_service.api.TestServiceOuterClass.GetRandomStringQuery;
import com.sixt.service.test_service.api.TestServiceOuterClass.RandomStringResponse;
import com.sixt.service.test_service.api.TestServiceOuterClass.SetHealthCheckStatusCommand;
import org.eclipse.jetty.client.HttpClient;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.List;

import static com.sixt.service.framework.rpc.RpcCallException.Category.InsufficientPermissions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class RandomServiceIntegrationTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    @Test
    public void testGetRandomString() throws Exception {
        GetRandomStringQuery request = GetRandomStringQuery.newBuilder().build();
        RandomStringResponse response = (RandomStringResponse) ServiceIntegrationTestSuite.testService.sendRequest(
                "TestService.GetRandomString", request);

        String result = response.getRandom();
        assertThat(result).matches("PRE:.*:POST");
    }

    @Test
    public void testSlowRespondingService() {
        try {
            ServiceIntegrationTestSuite.testService.sendRequest("TestService.SlowResponder",
                    GetRandomStringQuery.getDefaultInstance());
        } catch (RpcCallException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testHealthCheckOutputFormat() throws Exception {
        String failureMessage = "I'm a failure!";
        SetHealthCheckStatusCommand command = SetHealthCheckStatusCommand.newBuilder()
                .setStatus(HealthCheck.Status.FAIL.toString()).setMessage(failureMessage).build();
        //we have to work around the normal communication channels, as the load-balancer
        //won't let us talk to a failing instance
        LoadBalancer loadBalancer = ServiceIntegrationTestSuite.testService.getLoadBalancer();
        ServiceIntegrationTestSuite.testService.sendRequest("TestService.SetHealthCheckStatus", command);
        ServiceEndpoint endpoint = loadBalancer.getHealthyInstance();
        String url = "http://" + endpoint.getHostAndPort() + "/health";
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        String response = httpClient.GET(url).getContentAsString();
        assertThat(response).isEqualTo("{\"summary\":\"CRITICAL\",\"details\":[" +
                "{\"name\":\"database_migration\",\"status\":\"OK\",\"reason\":\"\"}," +
                "{\"name\":\"test_servlet\",\"status\":\"CRITICAL\",\"reason\":\"" + failureMessage + "\"}]}");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testKafkaMessageCounting() {
        String topic = "message-count";
        ServiceProperties serviceProperties = new ServiceProperties();
        serviceProperties.addProperty(ServiceProperties.KAFKA_SERVER_KEY,
                System.getenv(ServiceProperties.KAFKA_SERVER_KEY));
        KafkaPublisher publisher = new KafkaPublisherFactory(serviceProperties).newBuilder(topic).build();
        publisher.publishSync("test", "test", "test");
        TopicMessageCounter messageCounter = new TopicMessageCounter();
        long messageCount = messageCounter.getCount(System.getenv(ServiceProperties.KAFKA_SERVER_KEY), topic);
        assertThat(messageCount).isEqualTo(3);
    }

    @Test
    public void testKafkaEventHandlerAndPublisher() throws Exception {
        String message = "The message";
        String id = "some-id";
        ServiceIntegrationTestSuite.serviceImpersonator.publishEvent("events.RandomTopic", TestServiceOuterClass.RandomSampleEvent.newBuilder()
                .setMeta(TestServiceOuterClass.Meta.newBuilder().setName(TestServiceOuterClass.RandomSampleEvent.getDescriptor().getName()))
                .setId(id)
                .setMessage(message)
                .build());

        List<TestServiceOuterClass.HandlerSuccessEvent> publishedEvents = ServiceIntegrationTestSuite.testService
                .getEventsOfType(TestServiceOuterClass.HandlerSuccessEvent.class);
        assertThat(publishedEvents.size()).isEqualTo(1);
        assertThat(publishedEvents.get(0).getMessage()).isEqualTo(message);
        assertThat(publishedEvents.get(0).getId()).isEqualTo(id);
    }

    @Ignore //Does not work on a mac.  no route from docker service to impersonator on host
    @Test
    public void testImpersonatorThrowingException() {
        ServiceIntegrationTestSuite.serviceImpersonator.addMapping(CommandResponseMapping.newBuilder()
                .setCommand("AnotherService.ImpersonatorTest")
                .setException(new RpcCallException(InsufficientPermissions, "go away"))
                .build());
        try {
            ServiceIntegrationTestSuite.testService.sendRequest("TestService.CallsAnotherService",
                    TestServiceOuterClass.CallAnotherServiceCommand.getDefaultInstance());
            fail("Expected an RpcCallException");
        } catch (RpcCallException rpcEx) {
            assertThat(rpcEx.getCategory()).isEqualTo(InsufficientPermissions);
            assertThat(rpcEx.getMessage()).isEqualTo("go away");
        }
    }

}