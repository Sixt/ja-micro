package com.sixt.service.framework.kafka.messaging;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.DockerPort;
import com.sixt.service.framework.IntegrationTest;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// FIMXE does not work right now
@Ignore
@Category(IntegrationTest.class)
public class KafkaFailoverIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(Producer.class);

    @Rule
    public Timeout globalTimeout = Timeout.seconds(300);


    // FIXME clean up the compose file, and wait for all three instances to start
    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/test/resources/docker-compose-kafkafailover-integrationtest.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .projectName(ProjectName.random())
            .waitingForService("kafka", (container) -> DockerComposeHelper.waitForKafka(
                    "build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(2))
            .build();



    @Test
    public void manualKafkaTest() throws InterruptedException {

        ServiceProperties serviceProperties = fillServiceProperties();

        // Topics are created with 3 partitions - see docker-compose-kafkafailover-integrationtest.yml
        Topic ping = new Topic("ping");
        Topic pong = new Topic("pong");

        Producer producer = new ProducerFactory(serviceProperties).createProducer();

        final AtomicBoolean produceMessages = new AtomicBoolean(true);

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

                        sleeper.sleepNoException(250);
                    }
                } catch (Throwable t) {
                    logger.error("Exception in producer loop", t);
                }
            }
        });


        // Start first producer. It should get all 3 partitions assigned.
        Consumer consumer = consumerFactoryWithHandler(serviceProperties, SayHelloToCmd.class, new MessageHandler<SayHelloToCmd>() {
                    @Override
                    public void onMessage(Message<SayHelloToCmd> message, OrangeContext context) {
                    }
                }
        ).consumerForTopic(ping, new DiscardFailedMessages());


        // Wait to allow manual fiddling with Kafka
        Thread.sleep(300_000);


        produceMessages.set(false);
        producer.shutdown();
        consumer.shutdown();
    }

    private ServiceProperties fillServiceProperties() {
        DockerPort kafka = docker.containers().container("kafka").port(9092);
        DockerPort kafka2 = docker.containers().container("kafka2").port(9092);
        DockerPort kafka3 = docker.containers().container("kafka2").port(9092);

        StringBuilder kafkaServer = new StringBuilder();
        kafkaServer.append (kafka2.inFormat("$HOST:$EXTERNAL_PORT"));
        kafkaServer.append(",");
        kafkaServer.append (kafka.inFormat("$HOST:$EXTERNAL_PORT"));
        kafkaServer.append(",");
        kafkaServer.append (kafka3.inFormat("$HOST:$EXTERNAL_PORT"));

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
