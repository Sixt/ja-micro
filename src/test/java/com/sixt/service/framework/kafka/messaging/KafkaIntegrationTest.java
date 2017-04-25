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
import com.sixt.service.framework.servicetest.helper.StagedDockerComposeRule;
import com.sixt.service.framework.util.Sleeper;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.Duration;
import org.junit.*;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore //Ignore until we can properly fix the kafka SIT issue
@Category(IntegrationTest.class)
public class KafkaIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(KafkaIntegrationTest.class);

    @Rule
    public Timeout globalTimeout = Timeout.seconds(300);

    @ClassRule
    public static StagedDockerComposeRule docker = StagedDockerComposeRule.customBuilder()
            .file("src/test/resources/docker-compose-integrationtest.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .waitingForService("kafka", (container) -> DockerComposeHelper.waitForKafka(
                    "build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(2))
            .build();

    @BeforeClass
    public static void setupClass() throws Exception {
        DockerComposeHelper.setKafkaEnvironment(docker);
    }

    @Test
    public void simpleProducerConsumer() throws InterruptedException {
        ServiceProperties serviceProperties = new ServiceProperties();
        serviceProperties.initialize(new String[]{}); // Reads environment variables set by DockerComposeHelper

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

    @Test
    public void partitionAssignmentChange() throws InterruptedException {
        ServiceProperties serviceProperties = new ServiceProperties();
        serviceProperties.initialize(new String[]{}); // Reads environment variables set by DockerComposeHelper

        // Topics are created with 3 partitions - see docker-compose-integrationtest.yml
        Topic ping = new Topic("ping");
        Topic pong = new Topic("pong");

        Producer producer = new ProducerFactory(serviceProperties).createProducer();

        final AtomicBoolean produceMessages = new AtomicBoolean(true);
        final AtomicInteger sentMessages = new AtomicInteger(0);

        final AtomicInteger receivedMessagesConsumer1 = new AtomicInteger(0);
        final CountDownLatch firstMessageProcessedConsumer1 = new CountDownLatch(1);

        final AtomicInteger receivedMessagesConsumer2 = new AtomicInteger(0);
        final CountDownLatch firstMessageProcessedConsumer2 = new CountDownLatch(1);

        final AtomicInteger receivedMessagesConsumer3 = new AtomicInteger(0);
        final CountDownLatch firstMessageProcessedConsumer3 = new CountDownLatch(1);

        // Produce messages until test tells producer to stop.
        ExecutorService producerExecutor = Executors.newSingleThreadExecutor();
        producerExecutor.submit(new Runnable() {
            @Override
            public void run() {
                OrangeContext context = new OrangeContext();
                Sleeper sleeper = new Sleeper();

                try {
                    while (produceMessages.get()) {
                        String key = RandomStringUtils.randomAscii(5);
                        SayHelloToCmd payload = SayHelloToCmd.newBuilder().setName(key).build();

                        Message request = Messages.requestFor(ping, pong, key, payload, context);

                        producer.send(request);
                        sentMessages.incrementAndGet();

                        sleeper.sleepNoException(250);
                    }
                } catch (Throwable t) {
                    logger.error("Exception in producer loop", t);
                }
            }
        });

        // Start first producer. It should get all 3 partitions assigned.
        Consumer consumer1 = consumerFactoryWithHandler(serviceProperties, SayHelloToCmd.class, new MessageHandler<SayHelloToCmd>() {
                    @Override
                    public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
                        receivedMessagesConsumer1.incrementAndGet();
                        firstMessageProcessedConsumer1.countDown();
                    }
                }
        ).consumerForTopic(ping, new DiscardFailedMessages());

        // wait until consumer 1 is up.
        firstMessageProcessedConsumer1.await();
        Thread.sleep(5000); // consume some messages

        // Now, start second processor. It should get at least one partition assigned.
        Consumer consumer2 = consumerFactoryWithHandler(serviceProperties, SayHelloToCmd.class, new MessageHandler<SayHelloToCmd>() {
                    @Override
                    public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
                        receivedMessagesConsumer2.incrementAndGet();
                        firstMessageProcessedConsumer2.countDown();
                    }
                }
        ).consumerForTopic(ping, new DiscardFailedMessages());

        // wait until the second consumer is up.
        firstMessageProcessedConsumer2.await();
        Thread.sleep(5000); // let both consumers run a bit

        brutallyKillConsumer("pool-14-thread-1"); // consumer2 thread, HACKY: if this is too brittle, change the test to shutdown()

        //Need to wait a bit longer while Kafka "restabilizes the group" after consumer 2 was killed.
        // -> Consumer 1 should now get all three partitions back again.
        Thread.sleep(30000); // must be > than max.poll.interval.ms


        // Now, start third processor. It should get at least one partition assigned.
        Consumer consumer3 = consumerFactoryWithHandler(serviceProperties, SayHelloToCmd.class, new MessageHandler<SayHelloToCmd>() {
                    @Override
                    public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
                        receivedMessagesConsumer3.incrementAndGet();
                        firstMessageProcessedConsumer3.countDown();
                    }
                }
        ).consumerForTopic(ping, new DiscardFailedMessages());
        firstMessageProcessedConsumer3.await();
        Thread.sleep(5000);

        // Now shut down the first consumer.
        consumer1.shutdown();
        Thread.sleep(10000);

        // Stop the producer.
        produceMessages.set(false);
        producer.shutdown();
        producerExecutor.shutdown();

        Thread.sleep(3000); // give the remaining consumer the chance to consume all messages
        consumer3.shutdown(); // no assignment any longer

        // Finally, the assertions:
        int receivedMessagesTotal = receivedMessagesConsumer1.get() + receivedMessagesConsumer2.get() + receivedMessagesConsumer3.get();
        assertEquals(sentMessages.get(), receivedMessagesTotal);

        assertTrue(receivedMessagesConsumer1.get() > 0);
        assertTrue(receivedMessagesConsumer2.get() > 0);
        assertTrue(receivedMessagesConsumer3.get() > 0);
    }

    private void brutallyKillConsumer(String victimName) {
        int nbThreads = Thread.activeCount();
        Thread[] threads = new Thread[nbThreads];
        Thread.enumerate(threads);

        for (Thread t : threads) {
            if (t.getName().equals(victimName)) {
                logger.error("BOOM: Killing consumer thread {}", victimName);
                t.stop(); // used by intention despite deprecation
            }
        }
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
