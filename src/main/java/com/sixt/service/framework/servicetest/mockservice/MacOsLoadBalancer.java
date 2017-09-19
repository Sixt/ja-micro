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

package com.sixt.service.framework.servicetest.mockservice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.HttpClientWrapper;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.LoadBalancerUpdate;
import com.sixt.service.framework.rpc.ServiceEndpoint;
import com.sixt.service.framework.util.ProcessUtil;
import com.sixt.service.framework.util.Sleeper;
import io.opentracing.NoopTracerFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.google.common.collect.ImmutableList.of;

/**
* If we are on a Mac, we know we are on a developer machine and need to
* work around docker's crappy lack of bridged networking
*
* SIT setup requires the serviceUnderTest to run on port 40000 and be exposed
**/
public class MacOsLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(MacOsLoadBalancer.class);

    private final ProcessUtil processUtil;
    private String serviceName;
    private ServiceEndpoint impersonatorEndpoint;

    @Inject
    public MacOsLoadBalancer(ProcessUtil processUtil) {
        this.processUtil = processUtil;
    }

    @Override
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public HttpClientWrapper getHttpClientWrapper() {
        locateImpersonator();
        HttpClientWrapper retval = new HttpClientWrapper(new ServiceProperties(), createHttpClient(),
                null, NoopTracerFactory.create());
        retval.setLoadBalancer(this);
        return retval;
    }

    private void locateImpersonator() {
        int port = getPortFromDocker(ImpersonatedPortDictionary.getInstance().internalPortForImpersonated(serviceName));
        impersonatorEndpoint = new ServiceEndpoint(new ScheduledThreadPoolExecutor(1),
                "localhost:" + port, "");
    }

    protected int getPortFromDocker(int internalPort) {
        Sleeper sleeper = new Sleeper();
        String dockerJson = null;
        List<String> command = of("bash", "-c", "docker inspect `docker ps | grep " +
                internalPort + " | cut -f 1 -d ' '`");
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

    private HttpClient createHttpClient() {
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setExcludeCipherSuites("");
        HttpClient client = new HttpClient(sslContextFactory);
        client.setFollowRedirects(false);
        client.setMaxConnectionsPerDestination(2);
        //You can set more restrictive timeouts per request, but not less, so
        //  we set the maximum timeout of 1 hour here.
        client.setIdleTimeout(60 * 60 * 1000);
        try {
            client.start();
        } catch (Exception e) {
            logger.error("Error building http client", e);
        }
        return client;
    }

    @Override
    public ServiceEndpoint getHealthyInstance() {
        return impersonatorEndpoint;
    }

    @Override
    public ServiceEndpoint getHealthyInstanceExclude(List<ServiceEndpoint> triedEndpoints) {
        return impersonatorEndpoint;
    }

    @Override
    public void waitForServiceInstance() {
        //not needed?
    }

    @Override
    public void updateServiceEndpoints(LoadBalancerUpdate updates) {
        //not needed
    }

}
