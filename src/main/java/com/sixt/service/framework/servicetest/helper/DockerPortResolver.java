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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.sixt.service.framework.util.ProcessUtil;
import com.sixt.service.framework.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.google.common.collect.ImmutableList.of;

public class DockerPortResolver {

    private static final Logger logger = LoggerFactory.getLogger(DockerPortResolver.class);

    private final ProcessUtil processUtil;

    @Inject
    public DockerPortResolver(ProcessUtil processUtil) {
        this.processUtil = processUtil;
    }

    public int getExposedPortFromDocker(String serviceName, int internalPort) {
        Sleeper sleeper = new Sleeper();
        String dockerJson = null;
        List<String> command = of("bash", "-c", "docker inspect `docker ps | grep -w '" +
                internalPort + "/tcp' | cut -f 1 -d ' '`");
        int count = 0;
        while (count < 60) {
            dockerJson = processUtil.runProcess(command);
            if (dockerJson.startsWith("[")) {
                break;
            } else {
                sleeper.sleepNoException(1000);
            }
            count++;
        }
        int retval = -1;
        if (dockerJson != null && dockerJson.startsWith("[")) {
            retval = parseExposedPort(dockerJson, internalPort);
        }
        if (retval == -1) {
            logger.error("Could not determine host port mapping for {}, is it configured " +
                    "for port {} internally, and also exposed?", serviceName, internalPort);
        }
        return retval;
    }

    protected int parseExposedPort(String dockerJson, int internalPort) {
        try {
            JsonArray jobj = new JsonParser().parse(dockerJson).getAsJsonArray();
            JsonObject network = jobj.get(0).getAsJsonObject().get("NetworkSettings").getAsJsonObject();
            JsonObject ports = network.getAsJsonObject("Ports");
            JsonArray mappings = ports.getAsJsonArray("" + internalPort + "/tcp");
            if (mappings != null) {
                return mappings.get(0).getAsJsonObject().get("HostPort").getAsInt();
            }
        } catch (Exception ex) {
            logger.warn("Error parsing exposed port", ex);
        }
        return -1;
    }

}
