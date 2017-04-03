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

import com.google.protobuf.Parser;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.DockerPort;
import com.sixt.service.framework.IntegrationTest;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.protobuf.MessagingEnvelope;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.joda.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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



    @Test
    public void simpleProducerConsumer() throws InterruptedException {

        Thread.sleep(10000); // concurrency bug in the DockerComposeRule: wait for the topics to be created by startup script.

        DockerPort kafka = docker.containers().container("kafka").port(9092);
        String servers = kafka.inFormat("$HOST:$EXTERNAL_PORT");
        //String servers = "127.0.0.1:9092";

        Topic ping = new Topic("ping");
        Topic pong = new Topic("pong");


        Producer producer = new Producer(servers);

        MessagingEnvelope emptyMsg = MessagingEnvelope.getDefaultInstance();



        int N = 10;

        for (int i = 0; i<N; i++) {
            Message request = Messages.requestFor(ping, pong, RandomStringUtils.randomAscii(5), emptyMsg, new OrangeContext());
            producer.send(request);
        }


        CountDownLatch latch = new CountDownLatch(N);

        TypeDictionary typeDictionary = new TypeDictionary() {
            @Override
            public MessageHandler messageHandlerFor(MessageType type) {
                return new MessageHandler() {
                    @Override
                    public void onMessage(Message msg, OrangeContext context) {
                        Message reply = Messages.replyTo(msg, MessagingEnvelope.getDefaultInstance(), context);

                        producer.send(reply);
                        latch.countDown();
                    }
                };
            }

            @Override
            public Parser parserFor(MessageType type) {
                return MessagingEnvelope.parser();
            }
        };

        FailedMessageProcessor failedMessageProcessor = new DiscardFailedMessages();
        PartitionProcessorFactory ppf = new PartitionProcessorFactory(typeDictionary, failedMessageProcessor);


        Consumer consumer = new Consumer(ping, "test-group-42", servers, ppf);


        CountDownLatch latch2 = new CountDownLatch(N);

        TypeDictionary typeDictionary2 = new TypeDictionary() {
            @Override
            public MessageHandler messageHandlerFor(MessageType type) {
                return new MessageHandler() {
                    @Override
                    public void onMessage(Message msg, OrangeContext context) {
                        latch2.countDown();
                    }
                };
            }

            @Override
            public Parser parserFor(MessageType type) {
                return MessagingEnvelope.parser();
            }
        };

        PartitionProcessorFactory ppf2 = new PartitionProcessorFactory(typeDictionary2, failedMessageProcessor);

        Consumer consumer2 = new Consumer(pong, "test-group-43", servers, ppf2);

        latch.await(10, TimeUnit.SECONDS);
        latch2.await(10, TimeUnit.SECONDS);


        consumer.shutdown();
        consumer2.shutdown();
    }


}
