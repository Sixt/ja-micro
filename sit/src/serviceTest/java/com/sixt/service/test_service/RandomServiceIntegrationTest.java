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

import com.google.gson.JsonObject;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.rpc.ServiceEndpoint;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import com.sixt.service.framework.servicetest.mockservice.ServiceImpersonator;
import com.sixt.service.framework.servicetest.service.ServiceUnderTest;
import com.sixt.service.framework.servicetest.service.ServiceUnderTestImpl;
import com.sixt.service.test_service.api.TestServiceOuterClass;
import com.sixt.service.test_service.api.TestServiceOuterClass.GetRandomStringQuery;
import com.sixt.service.test_service.api.TestServiceOuterClass.RandomStringResponse;
import com.sixt.service.test_service.api.TestServiceOuterClass.SetHealthCheckStatusCommand;
import org.eclipse.jetty.client.HttpClient;
import org.joda.time.Duration;
import org.junit.*;
import org.junit.rules.Timeout;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RandomServiceIntegrationTest {

    private static ServiceUnderTest testService;

    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/serviceTest/resources/docker-compose.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .waitingForService("consul", (container) -> DockerComposeHelper.
                    waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1))
            .waitingForService("kafka", (container) -> DockerComposeHelper.
                    waitForKafka("build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(3))
            .projectName(ProjectName.random())
            .build();

    private static ServiceImpersonator serviceImpersonator;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    @BeforeClass
    public static void setupClass() throws Exception {
        DockerComposeHelper.setKafkaEnvironment(docker);
        DockerComposeHelper.setConsulEnvironment(docker);

        serviceImpersonator = new ServiceImpersonator("com.sixt.service.another-service");

        testService = new ServiceUnderTestImpl("com.sixt.service.test-service", "events");
    }

    @AfterClass
    public static void shutdown() {
        testService.shutdown();
        serviceImpersonator.shutdown();
    }

    @Test
    public void testGetRandomString() throws Exception {
        GetRandomStringQuery request = GetRandomStringQuery.newBuilder().build();
        RandomStringResponse response = (RandomStringResponse) testService.sendRequest(
                "TestService.GetRandomString", request);

        String result = response.getRandom();
        assertThat(result).matches("PRE:.*:POST");
    }

    @Test
    public void testSlowRespondingService() {
        try {
            testService.sendRequest("TestService.SlowResponder",
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
        LoadBalancer loadBalancer = testService.getLoadBalancer();
        ServiceEndpoint endpoint = loadBalancer.getHealthyInstance();
        testService.sendRequest("TestService.SetHealthCheckStatus", command);
        String url = "http://" + endpoint.getHostAndPort() + "/health";
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        String response = httpClient.GET(url).getContentAsString();
        assertThat(response).isEqualTo("{\"summary\":\"CRITICAL\",\"details\":[" +
                "{\"name\":\"database_migration\",\"status\":\"OK\",\"reason\":\"\"}," +
                "{\"name\":\"test_servlet\",\"status\":\"CRITICAL\",\"reason\":\"" + failureMessage + "\"}]}");
    }

    @Ignore // Test fails when run with gradle, but works when run in IntelliJ. Why? Reason:  org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms.
    @Test
    public void testRandomSampleEventHandler() throws Exception {
        serviceImpersonator.publishEvent("events", TestServiceOuterClass.RandomSampleEvent.newBuilder()
                .setId("some-id")
                .setMessage("The message")
                .build());

        List<JsonObject> receivedEvents = testService.getAllJsonEvents();

        assertThat(receivedEvents.size()).isEqualTo(1);
        TestServiceOuterClass.RandomSampleEvent event = ProtobufUtil.jsonToProtobuf(receivedEvents.get(0).toString(),
                TestServiceOuterClass.RandomSampleEvent.class);
        assertThat(event.getId()).isEqualTo("some-id");
        assertThat(event.getMessage()).isEqualTo("The message");
    }
}