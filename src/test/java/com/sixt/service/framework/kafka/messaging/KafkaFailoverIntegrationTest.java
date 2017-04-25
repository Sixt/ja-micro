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
import com.palantir.docker.compose.connection.DockerPort;
import com.sixt.service.framework.IntegrationTest;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import com.sixt.service.framework.servicetest.helper.StagedDockerComposeRule;
import com.sixt.service.framework.util.Sleeper;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.Duration;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

// This test is not automated and meant to be run manually: you kill/restart some Docker containers, look to the logs and see what happens.
@Ignore
@Category(IntegrationTest.class)
public class KafkaFailoverIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(KafkaFailoverIntegrationTest.class);

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60 * 20);


    @ClassRule
    public static StagedDockerComposeRule docker = StagedDockerComposeRule.customBuilder()
            .file("src/test/resources/docker-compose-kafkafailover-integrationtest.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .waitingForService("kafka1", (container) -> DockerComposeHelper.waitForKafka(
                    "build/dockerCompose/logs/kafka1.log"), Duration.standardMinutes(2))
            .waitingForService("kafka2", (container) -> DockerComposeHelper.waitForKafka(
                    "build/dockerCompose/logs/kafka2.log"), Duration.standardMinutes(2))
            .waitingForService("kafka3", (container) -> DockerComposeHelper.waitForKafka(
                    "build/dockerCompose/logs/kafka3.log"), Duration.standardMinutes(2))
            .build();


    @Test
    public void manualKafkaTest() throws InterruptedException {

        ServiceProperties serviceProperties = fillServiceProperties();

        // Topics are created with 3 partitions - see docker-compose-kafkafailover-integrationtest.yml
        Topic ping = new Topic("ping");
        Topic pong = new Topic("pong");

        AtomicInteger sentMessages = new AtomicInteger(0);
        AtomicInteger sendFailures = new AtomicInteger(0);
        AtomicInteger recievedMessages = new AtomicInteger(0);

        Producer producer = new ProducerFactory(serviceProperties).createProducer();

        final AtomicBoolean produceMessages = new AtomicBoolean(true);


        // Produce messages until test tells producer to stop.
        ExecutorService producerExecutor = Executors.newSingleThreadExecutor();
        producerExecutor.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        OrangeContext context = new OrangeContext();
                        Sleeper sleeper = new Sleeper();

                        while (produceMessages.get()) {
                            try {

                                String key = RandomStringUtils.randomAscii(5);
                                SayHelloToCmd payload = SayHelloToCmd.newBuilder().setName(key).build();

                                Message request = Messages.requestFor(ping, pong, key, payload, context);
                                producer.send(request);
                                sentMessages.incrementAndGet();

                                sleeper.sleepNoException(1000);
                            } catch (Throwable t) {
                                sendFailures.incrementAndGet();
                                logger.error("Caught exception in producer loop", t);
                            }
                        }
                    }
                }
        );


        Consumer consumer = consumerFactoryWithHandler(serviceProperties, SayHelloToCmd.class, new MessageHandler<SayHelloToCmd>() {
                    @Override
                    public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
                        recievedMessages.incrementAndGet();
                    }
                }
        ).consumerForTopic(ping, new DiscardFailedMessages());


        // Wait to allow manual fiddling with Kafka. Sync with global test timeout above.
        Thread.sleep(2* 60 * 1000);


        produceMessages.set(false);
        producer.shutdown();

        Thread.sleep(10_000);

        consumer.shutdown();

        logger.info("sentMessages: " + sentMessages.get());
        logger.info("sendFailures: " + sendFailures.get());
        logger.info("recievedMessages: " + recievedMessages.get());
    }


    @Ignore
    @Test
    public void producerSendsToNonExistingTopic() {
        ServiceProperties serviceProperties = fillServiceProperties();

        Topic cruft = new Topic("cruft");
        Topic lard = new Topic("lard");

        Producer producer = new ProducerFactory(serviceProperties).createProducer();

        String key = RandomStringUtils.randomAscii(5);
        SayHelloToCmd payload = SayHelloToCmd.newBuilder().setName(key).build();

        Message request = Messages.requestFor(cruft, lard, key, payload, new OrangeContext());

        producer.send(request);

        // Results:
        // 1.) NO topic auto creation i.e. KAFKA_AUTO_CREATE_TOPICS_ENABLE = false
        // 2017-04-12 18:14:41,239 [Time-limited test] DEBUG c.s.s.f.kafka.messaging.Producer - Sending message com.sixt.service.framework.kafka.messaging.SayHelloToCmd with key O+oRQ to topic cruft
        // loads of: 2017-04-12 18:14:41,340 [kafka-producer-network-thread | producer-2] WARN  o.apache.kafka.clients.NetworkClient - Error while fetching metadata with correlation id 0 : {cruft=UNKNOWN_TOPIC_OR_PARTITION}
        // and finally: org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms.
        // 2.) WITH topic auto creation i.e. KAFKA_AUTO_CREATE_TOPICS_ENABLE = true
        // 2017-04-12 18:18:24,488 [Time-limited test] DEBUG c.s.s.f.kafka.messaging.Producer - Sending message com.sixt.service.framework.kafka.messaging.SayHelloToCmd with key uXdJ~ to topic cruft
        // one: 2017-04-12 18:18:24,638 [kafka-producer-network-thread | producer-2] WARN  o.apache.kafka.clients.NetworkClient - Error while fetching metadata with correlation id 0 : {cruft=LEADER_NOT_AVAILABLE
        // and finally: success
    }


    @Ignore
    @Test
    public void consumerSubscribesToNonExistingTopic() throws InterruptedException {
        ServiceProperties serviceProperties = fillServiceProperties();

        Topic cruft = new Topic("krufty");

        CountDownLatch latch = new CountDownLatch(1);

        Consumer consumer = consumerFactoryWithHandler(serviceProperties, SayHelloToCmd.class, new MessageHandler<SayHelloToCmd>() {
                    @Override
                    public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
                        latch.countDown();
                    }
                }
        ).consumerForTopic(cruft, new DiscardFailedMessages());


        Producer producer = new ProducerFactory(serviceProperties).createProducer();

        String key = RandomStringUtils.randomAscii(5);
        SayHelloToCmd payload = SayHelloToCmd.newBuilder().setName(key).build();

        Message request = Messages.requestFor(cruft, cruft, key, payload, new OrangeContext());

        producer.send(request);


        assertTrue(latch.await(1, TimeUnit.MINUTES));
        producer.shutdown();
        consumer.shutdown();


        // Results:
        // 1.) WITH topic auto creation i.e. KAFKA_AUTO_CREATE_TOPICS_ENABLE = true
        // All ok, needs to discover coordinator etc.
        // 2.) NO topic auto creation i.e. KAFKA_AUTO_CREATE_TOPICS_ENABLE = false
        //2017-04-12 18:27:16,701 [pool-9-thread-1] INFO  c.s.s.f.kafka.messaging.Consumer - Consumer in group kruftmeister-com.sixt.service.unknown subscribed to topic kruftmeister
        //2017-04-12 18:27:16,852 [pool-9-thread-1] WARN  o.apache.kafka.clients.NetworkClient - Error while fetching metadata with correlation id 1 : {kruftmeister=UNKNOWN_TOPIC_OR_PARTITION}
        //2017-04-12 18:27:18,876 [pool-9-thread-1] WARN  o.apache.kafka.clients.NetworkClient - Error while fetching metadata with correlation id 40 : {kruftmeister=UNKNOWN_TOPIC_OR_PARTITION}
        //2017-04-12 18:27:18,889 [pool-9-thread-1] INFO  o.a.k.c.c.i.AbstractCoordinator - Discovered coordinator 172.19.0.3:9092 (id: 2147482646 rack: null) for group kruftmeister-com.sixt.service.unknown.
        //2017-04-12 18:27:18,892 [pool-9-thread-1] INFO  o.a.k.c.c.i.ConsumerCoordinator - Revoking previously assigned partitions [] for group kruftmeister-com.sixt.service.unknown
        //2017-04-12 18:27:18,894 [pool-9-thread-1] DEBUG c.s.s.f.kafka.messaging.Consumer - ConsumerRebalanceListener.onPartitionsRevoked on []
        //2017-04-12 18:27:18,917 [pool-9-thread-1] INFO  o.a.k.c.c.i.AbstractCoordinator - (Re-)joining group kruftmeister-com.sixt.service.unknown
        //2017-04-12 18:27:18,937 [pool-9-thread-1] INFO  o.a.k.c.c.i.AbstractCoordinator - Marking the coordinator 172.19.0.3:9092 (id: 2147482646 rack: null) dead for group kruftmeister-com.sixt.service.unknown
        //2017-04-12 18:27:19,041 [pool-9-thread-1] INFO  o.a.k.c.c.i.AbstractCoordinator - Discovered coordinator 172.19.0.3:9092 (id: 2147482646 rack: null) for group kruftmeister-com.sixt.service.unknown.
        //2017-04-12 18:27:19,041 [pool-9-thread-1] INFO  o.a.k.c.c.i.AbstractCoordinator - (Re-)joining group kruftmeister-com.sixt.service.unknown
        //2017-04-12 18:27:19,135 [pool-9-thread-1] INFO  o.a.k.c.c.i.AbstractCoordinator - Successfully joined group kruftmeister-com.sixt.service.unknown with generation 1
        //2017-04-12 18:27:19,135 [pool-9-thread-1] INFO  o.a.k.c.c.i.ConsumerCoordinator - Setting newly assigned partitions [] for group kruftmeister-com.sixt.service.unknown
        //2017-04-12 18:27:19,135 [pool-9-thread-1] DEBUG c.s.s.f.kafka.messaging.Consumer - ConsumerRebalanceListener.onPartitionsAssigned on []
        // -> assigned to a topic with no partitions?
    }

    private ServiceProperties fillServiceProperties() {
        DockerPort kafka1 = docker.containers().container("kafka1").port(9092);
        DockerPort kafka2 = docker.containers().container("kafka2").port(9092);
        DockerPort kafka3 = docker.containers().container("kafka2").port(9092);

        StringBuilder kafkaServer = new StringBuilder();
        kafkaServer.append(kafka2.inFormat("$HOST:$EXTERNAL_PORT"));
        kafkaServer.append(",");
        kafkaServer.append(kafka1.inFormat("$HOST:$EXTERNAL_PORT"));
        kafkaServer.append(",");
        kafkaServer.append(kafka3.inFormat("$HOST:$EXTERNAL_PORT"));

        String[] args = new String[2];
        args[0] = "-" + ServiceProperties.KAFKA_SERVER_KEY;
        args[1] = kafkaServer.toString();

        ServiceProperties serviceProperties = new ServiceProperties();
        serviceProperties.initialize(args);
        return serviceProperties;
    }

    private <T extends com.google.protobuf.Message> ConsumerFactory consumerFactoryWithHandler(ServiceProperties serviceProperties, Class<T> messageType, MessageHandler<T> handler) {
        TypeDictionary typeDictionary = new TypeDictionary();
        ReflectionTypeDictionaryFactory reflectionCruft = new ReflectionTypeDictionaryFactory(null);
        typeDictionary.putAllParsers(reflectionCruft.populateParsersFromClasspath());

        typeDictionary.putHandler(MessageType.of(messageType), handler);

        ConsumerFactory consumerFactory = new ConsumerFactory(serviceProperties, typeDictionary, null, null);
        return consumerFactory;
    }
}
