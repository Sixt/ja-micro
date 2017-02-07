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

import com.google.common.collect.Maps;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.DockerPort;
import com.palantir.docker.compose.connection.waiting.SuccessOrFailure;
import com.sixt.service.framework.ServiceProperties;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

public class DockerComposeHelper {

    private static final Logger logger = LoggerFactory.getLogger(DockerComposeHelper.class);

    /**
     * Set environment variable "kafkaServer" to the host and port specified by
     * docker
     */
    public static void setKafkaEnvironment(DockerComposeRule docker) {
        DockerPort kafka = docker.containers().container("kafka").port(9092);
        Map<String, String> newEnv = Maps.newHashMap();
        newEnv.put(ServiceProperties.KAFKA_SERVER_KEY, kafka.inFormat("$HOST:$EXTERNAL_PORT"));
        newEnv.putAll(System.getenv());
        setEnv(newEnv);
    }

    /**
     * Set environment variable "registryServer" to the host and port specified by
     * docker
     */
    public static void setConsulEnvironment(DockerComposeRule docker) {
        DockerPort consul = docker.containers().container("consul").port(8500);
        Map<String, String> newEnv = Maps.newHashMap();
        newEnv.put(ServiceProperties.REGISTRY_SERVER_KEY, consul.inFormat("$HOST:$EXTERNAL_PORT"));
        newEnv.putAll(System.getenv());
        setEnv(newEnv);
    }

    /**
     * Set environment variable "etcd_endpoints" to the host and port specified by
     * docker
     */
    public static void setEtcdEnvironment(DockerComposeRule docker) {
        DockerPort etcd = docker.containers().container("etcd").port(2379);
        Map<String, String> newEnv = Maps.newHashMap();
        newEnv.put("etcd_endpoints", etcd.inFormat("http://$HOST:$EXTERNAL_PORT"));
        newEnv.putAll(System.getenv());
        setEnv(newEnv);
    }

    /**
     * Get the server name of the postgres docker instance
     *
     * @param docker The used docker compose rule
     * @return postgresServerName
     */
    public static String getPostgresServerName(DockerComposeRule docker) {
        DockerPort postgres = docker.containers().container("postgres").port(5432);
        return postgres.inFormat("$HOST");
    }

    /**
     * Get the external port of the postgres docker instance
     *
     * @param docker The used docker compose rule
     * @return postgresExternalPort
     */
    public static String getPostgresExternalPort(DockerComposeRule docker) {
        DockerPort postgres = docker.containers().container("postgres").port(5432);
        return postgres.inFormat("$EXTERNAL_PORT");
    }

    /**
     * Wait for kafka to become ready by checking log for "Created topic events"
     * NOTE: For auto-creating topics, ensure the topic 'events' is last in the list.
     *
     * @param logFile The logfile to check
     * @return SuccessOrFailure
     */
    public static SuccessOrFailure waitForKafka(String logFile) {
        return waitFor(logFile, "Completed load of log events-0", "Kafka not ready yet");
    }

    /**
     * Wait for postgres to become ready by checking log for
     * "database system is ready to accept connections"
     *
     * @param logFile The logfile to check
     * @return SuccessOrFailure
     */
    public static SuccessOrFailure waitForPostgres(String logFile) {
        return waitForPostgres(logFile, "PostgreSQL init process complete; ready for start up");
    }

    /**
     * Wait for keycloak to become ready by checking log for
     * "Deployed \"keycloak-server.war\""
     *
     * @param logFile The logfile to check
     * @return SuccessOrFailure
     */
    public static SuccessOrFailure waitForKeycloak(String logFile) {
        return waitFor(logFile, "Deployed \"keycloak-server.war\"", "Keycloak is not ready yet");
    }

    /**
     * Wait for postgres to become ready by checking log for
     * some custom expected phrase
     *
     * @param logFile         The logfile to check
     * @param phraseToWaitFor The phrase in logs we wait for
     * @return SuccessOrFailure
     */
    public static SuccessOrFailure waitForPostgres(String logFile, String phraseToWaitFor) {
        return waitFor(logFile, phraseToWaitFor, "Postgres not ready yet");
    }

    /**
     * Wait for Consul to become ready by checking log for "consul: New leader elected"
     *
     * @param logFile The logfile to check
     * @return SuccessOrFailure
     */
    public static SuccessOrFailure waitForConsul(String logFile) {
        return waitFor(logFile, "consul: New leader elected", "Consul not ready yet");
    }

    /**
     * Wait for ETCD to become ready by checking log for "etcdserver: published"
     *
     * @param logFile The logfile to check
     * @return SuccessOrFailure
     */
    public static SuccessOrFailure waitForEtcd(String logFile) {
        return waitFor(logFile, "etcdserver: published", "ETCD not ready yet");
    }

    /**
     * Wait for Dynamo DB to become ready by checking log for "CorsParams:"
     *
     * @param logFile The logfile to check
     * @return SuccessOrFailure
     */
    public static SuccessOrFailure waitForDynamoDb(String logFile) {
        return waitFor(logFile, "CorsParams:", "Dynamo DB not ready yet");
    }
    
    private static SuccessOrFailure waitFor(String logFile, String expected, String timeoutMsg) {
        try {
            String log = FileUtils.readFileToString(new File(logFile), Charset.defaultCharset());
            if (log.contains(expected)) {
                return SuccessOrFailure.success();
            }
        } catch (IOException ex) {
            logger.error("Error waiting for container", ex);
        }

        return SuccessOrFailure.failure(timeoutMsg);
    }

    // Credits to: http://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java
    private static void setEnv(Map<String, String> newEnv) {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newEnv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newEnv);
        } catch (NoSuchFieldException e) {
            try {
                Class[] classes = Collections.class.getDeclaredClasses();
                Map<String, String> env = System.getenv();
                for (Class cl : classes) {
                    if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                        Field field = cl.getDeclaredField("m");
                        field.setAccessible(true);
                        Object obj = field.get(env);
                        Map<String, String> map = (Map<String, String>) obj;
                        map.clear();
                        map.putAll(newEnv);
                    }
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}
