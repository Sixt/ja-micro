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

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.kafka.TopicVerification;
import com.sixt.service.framework.kafka.messaging.*;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import com.sixt.service.framework.servicetest.helper.StagedDockerComposeRule;
import com.sixt.service.framework.util.Sleeper;
import com.sixt.service.test_service.api.Echo;
import com.sixt.service.test_service.api.Greeting;
import org.joda.time.Duration;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.ImmutableSet.of;
import static org.junit.Assert.assertTrue;


public class MessagingServiceIntegrationTest {

    @ClassRule
    public static StagedDockerComposeRule docker = StagedDockerComposeRule.customBuilder()
            .file("src/serviceTest/resources/docker-compose.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .stage("kafka")
            .service("kafka")
            .waitingForService("kafka", (container) -> DockerComposeHelper.
                    waitForKafka("build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(3))
            .stage("framework")
            .service("consul")
            .waitingForService("consul", (container) -> DockerComposeHelper.
                    waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1))
            .build();


    @Rule
    public Timeout globalTimeout = Timeout.seconds(300);

    @BeforeClass
    public static void setupClass() throws Exception {
        DockerComposeHelper.setKafkaEnvironment(docker);
    }

    @Test
    public void fireOneMessageToService() throws Exception {
        ServiceProperties sp = new ServiceProperties();
        sp.initialize(new String[]{}); // Reads environment variables set by DockerComposeHelper

        Topic testServiceInbox = Topic.defaultServiceInbox("com.sixt.service.test-service");
        Topic replyTo = new Topic("inbox_test");

        ensureTopicsExist(sp, of(testServiceInbox.toString(), replyTo.toString()));

        // Send a message...
        Producer producer = new ProducerFactory(sp).createProducer();

        Greeting hello = Greeting.newBuilder().setGreeting("Hello, world!").build();
        producer.send(Messages.requestFor(testServiceInbox, replyTo, "aKey", hello, new OrangeContext()));


        // ... start a consumer for the response ...
        CountDownLatch messageReceived = new CountDownLatch(1);

        TypeDictionary typeDictionary = new TypeDictionary();
        ReflectionTypeDictionaryFactory typeDictionaryFactory = new ReflectionTypeDictionaryFactory(null);
        typeDictionary.putAllParsers(typeDictionaryFactory.populateParsersFromClasspath());
        typeDictionary.putHandler(MessageType.of(Echo.class), new MessageHandler<Echo>() {
            @Override
            public void onMessage(Message<Echo> message, OrangeContext context) {
                System.err.println("Received response: " + message);
                messageReceived.countDown();
            }
        });

        ConsumerFactory consumerFactory = new ConsumerFactory(sp, typeDictionary);
        Consumer consumer = consumerFactory.consumerForTopic(replyTo, new DiscardFailedMessages());

        // ... and wait for the response.
        assertTrue(messageReceived.await(2, TimeUnit.MINUTES));

        producer.shutdown();
        consumer.shutdown();
    }



    private void ensureTopicsExist(ServiceProperties serviceProperties, Set<String> topics) {
        TopicVerification verifier = new TopicVerification();
        Sleeper sleeper = new Sleeper();

        while (!verifier.verifyTopicsExist(serviceProperties.getKafkaServer(), topics
                , false)) {
            sleeper.sleepNoException(100);
        }
    }

}