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

package com.sixt.service.framework.kafka;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.DockerPort;
import com.sixt.service.framework.IntegrationTest;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.Duration;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;

import static com.sixt.service.framework.ServiceProperties.KAFKA_SERVER_KEY;

@Ignore //Used to verify throttling, but would need more work to make it fully automated
        //with deterministic results
@Category(IntegrationTest.class)
public class KafkaThrottlingTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(660);

    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/test/resources/docker-compose-integrationtest.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .projectName(ProjectName.random())
            .waitingForService("kafka", (container) -> DockerComposeHelper.waitForKafka(
                    "build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(1))
            .build();

    @Test
    public void throttleTest() throws InterruptedException {
        int messageCount = 200;
        CountDownLatch latch = new CountDownLatch(messageCount);

        DockerPort kafka = docker.containers().container("kafka").port(9092);
        ServiceProperties props = new ServiceProperties();
        props.addProperty(KAFKA_SERVER_KEY, kafka.inFormat("$HOST:$EXTERNAL_PORT"));

        String topic = "throttle-test";
        KafkaPublisherFactory publisherFactory = new KafkaPublisherFactory(props, null);
        KafkaPublisher publisher = publisherFactory.newBuilder(topic).build();

        KafkaSubscriberFactory<String> subscriberFactory = new KafkaSubscriberFactory<>(props, null);
        EventReceivedCallback<String> callback = (message, topicInfo) -> {
            latch.countDown();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        subscriberFactory.newBuilder(topic, callback).withPollTime(50).withAutoCommit(true).build();

        for (int i = 0; i < messageCount; i++) {
            publisher.publishSync("message " + i + randomData());
        }
        latch.await();
    }

    private String randomData() {
        return RandomStringUtils.random(4096, "abcdefghijklmnopqrstuvwxyz");
    }

}
