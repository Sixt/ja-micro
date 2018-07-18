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

package com.sixt.service.framework;

import com.jcabi.manifests.Manifests;
import com.sixt.service.framework.logging.SixtLogbackContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServiceProperties {

    private static final Logger logger = LoggerFactory.getLogger(ServiceProperties.class);

    public static final String REGISTRY_SERVER_KEY = "registryServer";
    public static final String KAFKA_SERVER_KEY = "kafkaServer";
    public static final String KAFKA_PASSWORD_KEY = "kafkaPassword";
    public static final String DATABASE_SERVER_KEY = "databaseServer";
    public static final String DATABASE_USERNAME_KEY = "databaseUsername";
    public static final String DATABASE_PASSWORD_KEY = "databasePassword";
    public static final String SERVICE_PORT_KEY = "servicePort";
    public static final String LOG_LEVEL_KEY = "logLevel";
    public static final String SERVICE_UNKNOWN = "com.sixt.service.unknown";

    private String serviceName = SERVICE_UNKNOWN;
    private String serviceVersion = "0.0.0-fffffff";
    private String serviceInstanceId = "unknown";
    private int servicePort = 0;
    private Map<String, String> allProperties = new HashMap<>();

    protected SixtLogbackContext logbackContext = new SixtLogbackContext();

    public ServiceProperties() {
    }

    /**
     * Parse the command-line arguments.  Framework-level (standard arguments for
     * all Java services) are parsed, and any remaining arguments and environment
     * variables are set into our properties map as well.
     */
    public void initialize(String[] args) {
        parseServiceProperties();
        parseCommandLineArguments(args);
        parseEnvironmentVariables();
        String logLevel = allProperties.get(LOG_LEVEL_KEY);
        if (! StringUtils.isBlank(logLevel)) {
            System.setProperty("SIXT_LOGGING", logLevel);
            logbackContext.updateLoggerLogLevel(logLevel);
        }
        if (allProperties.containsKey(SERVICE_PORT_KEY)) {
            setServicePort(Integer.parseInt(allProperties.get(SERVICE_PORT_KEY)));
        }
    }

    private void parseServiceProperties() {
        try {
            String name = Manifests.read("Service-Title");
            setServiceName(name);
        } catch (Exception ex) {
            logger.info("Error finding Service-Title in manifests", ex);
        }
        try {
            serviceVersion = Manifests.read("Service-Version");
        } catch (Exception ex) {
            logger.info("Error finding Service-Version in manifests", ex);
        }
        parseServiceInstance();
    }

    /**
     * If running in docker, use that; else, generate test_service
     */
    private void parseServiceInstance() {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                    "cat /proc/self/cgroup | grep docker | sed 's/^.*\\///' | tail -n1 | cut -c 1-12");
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output = reader.readLine();
            if (! StringUtils.isBlank(output)) {
                serviceInstanceId = output.trim();
            }
            p.waitFor();
        } catch (Exception e) {
            logger.error("Error getting docker container id", e);
        }
        if ("unknown".equals(serviceInstanceId)) {
            serviceInstanceId = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12);
        }
    }

    private void parseCommandLineArguments(String[] args) {
        if (args.length % 2 == 1) {
            throw new IllegalArgumentException("Expecting even number of arguments");
        }
        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            while (key.startsWith("-")) {
                key = key.substring(1);
            }
            String value = args[i + 1];
            logger.debug("Setting property from cmd-line {} to {}", key, value);
            allProperties.put(key, value);
        }
    }

    private void parseEnvironmentVariables() {
        Map<String, String> env = System.getenv();
        for (String key : env.keySet()) {
            if (key.startsWith("LC_") || key.startsWith("_")) {
                continue;
            }
            String value = env.get(key);
            logger.debug("Setting property from environment {} to {}", key, value);
            allProperties.put(key, value);
        }
    }

    public void ensureProperties(String[] requiredProps) {
        boolean fail = ! areAllPropertiesSet(requiredProps);
        if (fail) {
            throw new IllegalStateException("EnsureProperties failed");
        }
    }

    public boolean areAllPropertiesSet(String[] requiredProps) {
        boolean fail = true;
        if (requiredProps != null) {
            for (String prop : requiredProps) {
                if (allProperties.get(prop) == null) {
                    logger.info("Required property {} was not set", prop);
                    fail = false;
                }
            }
        }
        return fail;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public String getServiceInstanceId() {
        return serviceInstanceId;
    }

    public void setServicePort(int servicePort) {
        this.servicePort = servicePort;
    }

    public int getServicePort() {
        return servicePort;
    }

    /**
     * Consul server in the form of host:port
     */
    public String getRegistryServer() {
        return allProperties.get(REGISTRY_SERVER_KEY);
    }

    public String getKafkaServer() {
        return allProperties.get(KAFKA_SERVER_KEY);
    }

    public String getKafkaPassword() {
        return allProperties.get(KAFKA_PASSWORD_KEY);
    }

    public String getDatabaseServer() {
        return allProperties.get(DATABASE_SERVER_KEY);
    }

    public String getDatabaseUsername() {
        return allProperties.get(DATABASE_USERNAME_KEY);
    }

    public String getDatabasePassword() {
        return allProperties.get(DATABASE_PASSWORD_KEY);
    }

    public void addProperty(String key, String value) {
        allProperties.put(key, value);
    }

    public String getProperty(String key) {
        return allProperties.get(key);
    }

    public Map<String, String> getAllProperties() {
        return allProperties;
    }

    public int getIntegerProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (StringUtils.isNotBlank(value)) {
            try {
                return Integer.valueOf(value);
            } catch (Exception ex) {
                logger.info("Service property '{}' was not an integer value: '{}'", key, value);
            }
        }
        return defaultValue;
    }

    //Intended only for testing
    public void setServiceName(String name) {
        serviceName = name;
    }

    //Intended only for testing
    public void setServiceInstanceId(String id) {
        serviceInstanceId = id;
    }

}
