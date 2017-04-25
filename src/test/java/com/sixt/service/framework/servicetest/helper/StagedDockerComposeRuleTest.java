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

package com.sixt.service.framework.servicetest.helper;

import com.palantir.docker.compose.configuration.ProjectName;
import org.joda.time.Duration;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Ignore("Takes several minutes to run. Test if containers really start is not automated.")
public class StagedDockerComposeRuleTest {

    private final static Logger log = LoggerFactory.getLogger(StagedDockerComposeRuleTest.class);


    @Test
    public void useWithoutStages() throws IOException, InterruptedException {
        // No stages -> all should go to default stage
        StagedDockerComposeRule sdcr = StagedDockerComposeRule.customBuilder()
                .file("sit/src/serviceTest/resources/docker-compose.yml")
                .saveLogsTo("build/dockerCompose/logs")
                .waitingForService("consul", (container) -> DockerComposeHelper.
                        waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1))
                .waitingForService("kafka", (container) -> DockerComposeHelper.
                        waitForKafka("build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(2))
                .projectName(ProjectName.fromString("cruft"))
                .build();

        List<StagedDockerComposeRule.Stage> stages = sdcr.stages();
        assertEquals(1, stages.size());

        StagedDockerComposeRule.Stage defaultStage = stages.get(0);
        assertEquals("default", defaultStage.getName());
        assertEquals(0, defaultStage.getServices().size());
        assertEquals(2, defaultStage.getWaits().size());

        try {
            log.debug("pre before()");
            sdcr.before();
            log.debug("post before()");

        } finally {
            log.debug("pre after()");
            sdcr.after();
            log.debug("post after()");
        }

    }


    @Test
    public void useExplicitStage() throws IOException, InterruptedException {
        StagedDockerComposeRule sdcr = StagedDockerComposeRule.customBuilder()
                .file("sit/src/serviceTest/resources/docker-compose.yml")  // Global setting
                .stage("infrastructure") // Opens a stage -> current stage
                .service("kafka") // Binds to current stage
                .service("consul") // Binds to current stage
                .waitingForService("kafka", (container) -> DockerComposeHelper.
                        waitForKafka("build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(3)) // Binds to current stage
                .waitingForService("consul", (container) -> DockerComposeHelper.
                        waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1)) // Binds to current stage
                // remaining services start in default stage
                .saveLogsTo("build/dockerCompose/logs") // Global setting
                .build();

        List<StagedDockerComposeRule.Stage> stages = sdcr.stages();
        assertEquals(2, stages.size());

        StagedDockerComposeRule.Stage explictStage = stages.get(0);
        assertEquals("infrastructure", explictStage.getName());
        assertEquals(2, explictStage.getServices().size());
        assertEquals(2, explictStage.getWaits().size());


        StagedDockerComposeRule.Stage defaultStage = stages.get(1);
        assertEquals("default", defaultStage.getName());
        assertEquals(0, defaultStage.getServices().size());
        assertEquals(0, defaultStage.getWaits().size());

        try {
            log.debug("pre before()");
            sdcr.before();
            log.debug("post before()");

        } finally {
            log.debug("pre after()");
            sdcr.after();
            log.debug("post after()");
        }
    }


    @Test
    public void useMultipleStages() throws IOException, InterruptedException {
        StagedDockerComposeRule sdcr = StagedDockerComposeRule.customBuilder()
                .file("sit/src/serviceTest/resources/docker-compose.yml")  // Global setting
                .stage("kafka")
                .service("kafka")
                .saveLogsTo("build/dockerCompose/logs") // Global setting, mixed in at any point
                .waitingForService("kafka", (container) -> DockerComposeHelper.
                        waitForKafka("build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(3))
                .stage("framework")
                .service("consul") // Binds to current stage
                .waitingForService("consul", (container) -> DockerComposeHelper.
                        waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1))
                .stage("SUT") // Should be not required -> default stage, includes all remaining services (i.e. unqualified compose up)
                .build();

        List<StagedDockerComposeRule.Stage> stages = sdcr.stages();
        assertEquals(4, stages.size());

        StagedDockerComposeRule.Stage explictStage = stages.get(0);
        assertEquals("kafka", explictStage.getName());
        assertEquals(1, explictStage.getServices().size());
        assertEquals(1, explictStage.getWaits().size());

        explictStage = stages.get(1);
        assertEquals("framework", explictStage.getName());
        assertEquals(1, explictStage.getServices().size());
        assertEquals(1, explictStage.getWaits().size());

        explictStage = stages.get(2);
        assertEquals("SUT", explictStage.getName());
        assertEquals(0, explictStage.getServices().size());
        assertEquals(0, explictStage.getWaits().size());

        StagedDockerComposeRule.Stage defaultStage = stages.get(3);
        assertEquals("default", defaultStage.getName());
        assertEquals(0, defaultStage.getServices().size());
        assertEquals(0, defaultStage.getWaits().size());

        try {
            log.debug("pre before()");
            sdcr.before();
            log.debug("post before()");

        } finally {
            log.debug("pre after()");
            sdcr.after();
            log.debug("post after()");
        }
    }


    @Test
    public void mixingDefaultAndExplicitStage_noExplictDefault() throws IOException, InterruptedException {
        StagedDockerComposeRule sdcr = StagedDockerComposeRule.customBuilder()
                .file("sit/src/serviceTest/resources/docker-compose.yml")  // Global setting
                .waitingForService("consul", (container) -> DockerComposeHelper.  // Binds to default stage which is executed after the explicit stage.
                        waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1))
                .stage("infrastructure") // Opens a stage -> current stage
                .service("kafka") // Binds to stage infrastructure
                .waitingForService("kafka", (container) -> DockerComposeHelper.
                        waitForKafka("build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(3)) // Binds to stage infrastructure
                // remaining services start in default stage
                .saveLogsTo("build/dockerCompose/logs") // Global setting
                .build();

        List<StagedDockerComposeRule.Stage> stages = sdcr.stages();
        assertEquals(2, stages.size());

        StagedDockerComposeRule.Stage explictStage = stages.get(0);
        assertEquals("infrastructure", explictStage.getName());
        assertEquals(1, explictStage.getServices().size());
        assertEquals(1, explictStage.getWaits().size());

        StagedDockerComposeRule.Stage defaultStage = stages.get(1);
        assertEquals("default", defaultStage.getName());
        assertEquals(0, defaultStage.getServices().size());
        assertEquals(1, defaultStage.getWaits().size());

        try {
            log.debug("pre before()");
            sdcr.before();
            log.debug("post before()");

        } finally {
            log.debug("pre after()");
            sdcr.after();
            log.debug("post after()");
        }
    }


    @Test
    public void mixingDefaultAndExplicitStage_ExplictDefault() throws IOException, InterruptedException {
        StagedDockerComposeRule sdcr = StagedDockerComposeRule.customBuilder()
                .file("sit/src/serviceTest/resources/docker-compose.yml")  // Global setting
                .saveLogsTo("build/dockerCompose/logs") // Global setting
                .stage("infrastructure") // Opens a stage -> current stage
                .service("kafka") // Binds to stage infrastructure
                .waitingForService("kafka", (container) -> DockerComposeHelper.
                        waitForKafka("build/dockerCompose/logs/kafka.log"), Duration.standardMinutes(3)) // Binds to stage infrastructure
                .defaultStage() // open explicit context for default stage
                .waitingForService("consul", (container) -> DockerComposeHelper.  // Binds to default stage which is executed after the explicit stage.
                        waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1))
                .build();

        List<StagedDockerComposeRule.Stage> stages = sdcr.stages();
        assertEquals(2, stages.size());

        StagedDockerComposeRule.Stage explictStage = stages.get(0);
        assertEquals("infrastructure", explictStage.getName());
        assertEquals(1, explictStage.getServices().size());
        assertEquals(1, explictStage.getWaits().size());

        StagedDockerComposeRule.Stage defaultStage = stages.get(1);
        assertEquals("default", defaultStage.getName());
        assertEquals(0, defaultStage.getServices().size());
        assertEquals(1, defaultStage.getWaits().size());

        try {
            log.debug("pre before()");
            sdcr.before();
            log.debug("post before()");

        } finally {
            log.debug("pre after()");
            sdcr.after();
            log.debug("post after()");
        }
    }
}