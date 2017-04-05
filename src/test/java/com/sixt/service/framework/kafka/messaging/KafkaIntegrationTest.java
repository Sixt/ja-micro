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
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.Duration;
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


    // FIXME
    public final static int N = 10;
    public static CountDownLatch requestLatch = new CountDownLatch(N);
    public static CountDownLatch responseLatch = new CountDownLatch(N);
    public static String servers;

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
        Thread.sleep(10000); // TODO concurrency bug in the DockerComposeRule: wait for the topics to be created by startup script.

        Topic ping = new Topic("ping");
        Topic pong = new Topic("pong");

        servers = docker.containers().container("kafka").port(9092).inFormat("$HOST:$EXTERNAL_PORT");


        Producer producer = new Producer(servers);



        TypeDictionary typeDictionary = new ReflectionTypeDictionaryFactory().createTypeDictionaryFromClasspath();
        PartitionProcessorFactory ppf = new PartitionProcessorFactory(typeDictionary, new DiscardFailedMessages());

        Consumer requestConsumer = new Consumer(ping, "consumer-group-test-1", servers, ppf);
        Consumer replyConsumer = new Consumer(pong, "consumer-group-test-1", servers, ppf);

        for (int i = 0; i < N; i++) {
            SayHelloToCmd cmd = SayHelloToCmd.newBuilder().setName(Integer.toString(i)).build();
            Message request = Messages.requestFor(ping, pong, RandomStringUtils.randomAscii(5), cmd, new OrangeContext());
            producer.send(request);
        }

        assertTrue(requestLatch.await(10, TimeUnit.SECONDS));
        assertTrue(responseLatch.await(10, TimeUnit.SECONDS));

        producer.shutdown();
        requestConsumer.shutdown();
        replyConsumer.shutdown();
    }

}



class SayHelloToCmdHandler implements MessageHandler<SayHelloToCmd> {

    Producer producer;

    @Override
    public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
        if (producer == null) {
            producer = new Producer(KafkaIntegrationTest.servers);
        }

        System.err.println(message);

        SayHelloToReply greeting = SayHelloToReply.newBuilder().setGreeting("Hello to " + message.getPayload().getName()).build();
        Message reply = Messages.replyTo(message, greeting, context);

        producer.send(reply);
        KafkaIntegrationTest.requestLatch.countDown();

    }
}

class SayHelloToReplyHandler implements MessageHandler<SayHelloToReply> {


    @Override
    public void onMessage(Message<SayHelloToReply> message, OrangeContext context) {
        // FIXME
        System.err.println(message);

        KafkaIntegrationTest.responseLatch.countDown();
    }
}