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

package com.sixt.service.framework.kafka.messaging;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.sixt.service.framework.IntegrationTest;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.Duration;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class KafkaIntegrationTest {


    @Rule
    public Timeout globalTimeout = Timeout.seconds(300);


    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/test/resources/docker-compose-integrationtest.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .projectName(ProjectName.random())
            .waitingForService("kafka", (container) -> DockerComposeHelper.waitForKafka(
                    "build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(1))
            .build();


    @BeforeClass
    public static void setupClass() throws Exception {
        DockerComposeHelper.setKafkaEnvironment(docker);
    }


    @Test
    public void simpleProducerConsumer() throws InterruptedException {
        ServiceProperties serviceProperties = new ServiceProperties();
        serviceProperties.initialize(new String[]{}); // Reads environment variables set by DockerComposeHelper

        Thread.sleep(5000); // TODO concurrency bug in the DockerComposeRule: wait for the topics to be created by startup script.


        Topic ping = new Topic("ping");
        Topic pong = new Topic("pong");
        final int N = 10;

        Producer producer = new ProducerFactory(serviceProperties).createProducer();
        for (int i = 0; i < N; i++) {
            SayHelloToCmd cmd = SayHelloToCmd.newBuilder().setName(Integer.toString(i)).build();
            Message request = Messages.requestFor(ping, pong, RandomStringUtils.randomAscii(5), cmd, new OrangeContext());
            producer.send(request);
        }


        final CountDownLatch requestLatch = new CountDownLatch(N);
        final CountDownLatch responseLatch = new CountDownLatch(N);


        TypeDictionary typeDictionary = new TypeDictionary();
        ReflectionTypeDictionaryFactory typeDictionaryFactory = new ReflectionTypeDictionaryFactory(null);
        typeDictionary.putAllParsers(typeDictionaryFactory.populateParsersFromClasspath());

        typeDictionary.putHandler(MessageType.of(SayHelloToCmd.class), new MessageHandler<SayHelloToCmd>() {
            @Override
            public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
                System.err.println(message);

                SayHelloToReply greeting = SayHelloToReply.newBuilder().setGreeting("Hello to " + message.getPayload().getName()).build();
                Message reply = Messages.replyTo(message, greeting, context);

                producer.send(reply);
                requestLatch.countDown();

            }
        });

        typeDictionary.putHandler(MessageType.of(SayHelloToReply.class), new MessageHandler<SayHelloToReply>() {
            @Override
            public void onMessage(Message<SayHelloToReply> message, OrangeContext context) {
                System.err.println(message);
                responseLatch.countDown();
            }
        });


        ConsumerFactory consumerFactory = new ConsumerFactory(serviceProperties, typeDictionary, null, null);
        Consumer requestConsumer = consumerFactory.consumerForTopic(ping, new DiscardFailedMessages());
        Consumer replyConsumer = consumerFactory.consumerForTopic(pong, new DiscardFailedMessages());


        assertTrue(requestLatch.await(60, TimeUnit.SECONDS));
        assertTrue(responseLatch.await(60, TimeUnit.SECONDS));

        producer.shutdown();
        requestConsumer.shutdown();
        replyConsumer.shutdown();
    }

}
