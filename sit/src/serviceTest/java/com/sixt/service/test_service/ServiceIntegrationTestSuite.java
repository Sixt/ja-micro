package com.sixt.service.test_service;


import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.Container;
import com.sixt.service.framework.servicetest.helper.DockerComposeHelper;
import com.sixt.service.framework.servicetest.mockservice.ServiceImpersonator;
import com.sixt.service.framework.servicetest.service.ServiceUnderTest;
import com.sixt.service.framework.servicetest.service.ServiceUnderTestImpl;
import org.joda.time.Duration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        MessagingServiceIntegrationTest.class,
        RandomServiceIntegrationTest.class,
})
public class ServiceIntegrationTestSuite {


    static ServiceImpersonator serviceImpersonator;
    static ServiceUnderTest testService;


    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/serviceTest/resources/docker-compose.yml")
            .saveLogsTo("build/dockerCompose/logs")
            .waitingForService("consul", (container) -> DockerComposeHelper.
                    waitForConsul("build/dockerCompose/logs/consul.log"), Duration.standardMinutes(1))
            .waitingForService("com.sixt.service.test-service", Container::areAllPortsOpen, Duration.standardMinutes(1))
            .build();


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

}
