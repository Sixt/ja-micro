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

package com.sixt.service.framework.logging;

import ch.qos.logback.core.spi.DeferredProcessingAware;
import com.fasterxml.jackson.core.JsonGenerator;
import com.jcabi.manifests.Manifests;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

public class ServicePropertiesProvider extends AbstractFieldJsonProvider {

    private String serviceName;
    private String serviceVersion;
    private String serviceInstanceId;

    public ServicePropertiesProvider() {
        parseServiceProperties();
    }

    private void parseServiceProperties() {
        try {
            serviceName = Manifests.read("Service-Title");
        } catch (Exception ex) {
            serviceName = "com.sixt.service.unknown";
        }
        try {
            serviceVersion = Manifests.read("Service-Version");
        } catch (Exception ex) {
            serviceVersion = "0.0.0-fffffff";
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
            System.err.println("Error getting docker container id");
            e.printStackTrace();
        }
        if (serviceInstanceId == null) {
            serviceInstanceId = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12);
        }
    }

    @Override
    public void writeTo(JsonGenerator generator, DeferredProcessingAware deferredProcessingAware) throws IOException {
        generator.writeFieldName("service");
        generator.writeString(serviceName);
        generator.writeFieldName("service-version");
        generator.writeString(serviceVersion);
        generator.writeFieldName("service-id");
        generator.writeString(serviceInstanceId);
    }

}
