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


import com.google.common.base.Preconditions;
import com.palantir.docker.compose.execution.DockerCompose;
import com.palantir.docker.compose.logging.LogCollector;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * DynamicFileLogCollector allows to add services as they have been started to the set of monitored log files.
 * <p>
 * Precondition: services need to be started (docker-compose up) before you can capture their logs.
 */
public class DynamicFileLogCollector implements LogCollector {
    // Partially copy-pasted from com.palantir.docker.compose.logging.FileLogCollector

    private static final Logger log = LoggerFactory.getLogger(DynamicFileLogCollector.class);
    private static final long STOP_TIMEOUT_IN_MILLIS = 50L;
    private final File logDirectory;
    private final ExecutorService executor = Executors.newCachedThreadPool(); // Creates an own thread for each log file to capture
    private final Set<String> observedServices = new HashSet<>();

    public DynamicFileLogCollector(File logDirectory) {
        Preconditions.checkArgument(!logDirectory.isFile(), "Log directory cannot be a file");
        if (!logDirectory.exists()) {
            Validate.isTrue(logDirectory.mkdirs(), "Error making log directory: " + logDirectory.getAbsolutePath());
        }

        this.logDirectory = logDirectory;
    }

    public static DynamicFileLogCollector fromPath(String path) {
        return new DynamicFileLogCollector(new File(path));
    }

    /**
     * Collect logs of all services listed in the compose files.
     *
     * @param dockerCompose
     * @throws IOException
     * @throws InterruptedException
     */
    public synchronized void startCollecting(DockerCompose dockerCompose) throws IOException, InterruptedException {
        for (String service : dockerCompose.services()) {
            if (observedServices.contains(service)) {
                continue;
            }

            observedServices.add(service);
            collectLogs(service, dockerCompose);
        }
    }

    /**
     * Incrementally adds newly started services to the set of collected logs.
     *
     * @param dockerCompose
     * @throws IOException
     * @throws InterruptedException
     */
    public synchronized void startCollecting(DockerCompose dockerCompose, String service) throws IOException, InterruptedException {
        if (observedServices.contains(service)) {
            return;
        }

        observedServices.add(service);
        collectLogs(service, dockerCompose);
    }


    private void collectLogs(String container, DockerCompose dockerCompose) {
        executor.submit(() -> {
            File outputFile = new File(logDirectory, container + ".log");
            log.info("Writing logs for container '{}' to '{}'", container, outputFile.getAbsolutePath());
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                dockerCompose.writeLogs(container, outputStream);  // docker-compose logs --follow
            } catch (IOException e) {
                throw new RuntimeException("Error reading log", e);
            }
        });
    }

    public synchronized void stopCollecting() throws InterruptedException {

        // This policy for a graceful shut down does not really work, but since we throw away the JVM, who cares?
        if (!executor.awaitTermination(50L, TimeUnit.MILLISECONDS)) {
            log.debug("docker containers were still running when log collection stopped");
            executor.shutdownNow();
        }
    }
}

