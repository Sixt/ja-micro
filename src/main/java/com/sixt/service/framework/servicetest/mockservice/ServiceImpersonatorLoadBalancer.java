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

import com.google.inject.Inject;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.HttpClientWrapper;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.LoadBalancerUpdate;
import com.sixt.service.framework.rpc.ServiceEndpoint;
import com.sixt.service.framework.servicetest.helper.DockerPortResolver;
import io.opentracing.NoopTracerFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ServiceImpersonatorLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(ServiceImpersonatorLoadBalancer.class);

    protected String serviceName;
    protected ServiceEndpoint serviceEndpoint;
    protected final DockerPortResolver dockerPortResolver;
    protected HttpClientWrapper httpClientWrapper;

    @Inject
    public ServiceImpersonatorLoadBalancer(DockerPortResolver dockerPortResolver) {
        this.dockerPortResolver = dockerPortResolver;
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
    public synchronized HttpClientWrapper getHttpClientWrapper() {
        //we have to do lazy initialization because the service name needs to be correctly set
        if (httpClientWrapper == null) {
            httpClientWrapper = createHttpClientWrapper();
        }
        return httpClientWrapper;
    }

    protected HttpClientWrapper createHttpClientWrapper() {
        int port = locateTargetServicePort();
        serviceEndpoint = new ServiceEndpoint(new ScheduledThreadPoolExecutor(1),
                "localhost:" + port, "");
        HttpClientWrapper retval = new HttpClientWrapper(new ServiceProperties(), createHttpClient(),
                null, NoopTracerFactory.create());
        retval.setLoadBalancer(this);
        return retval;
    }

    protected int locateTargetServicePort() {
        int internalPort = ImpersonatedPortDictionary
                .getInstance().getInternalPortForImpersonated(serviceName);
        return dockerPortResolver.getExposedPortFromDocker(serviceName, internalPort);
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
        return serviceEndpoint;
    }

    @Override
    public ServiceEndpoint getHealthyInstanceExclude(List<ServiceEndpoint> triedEndpoints) {
        return serviceEndpoint;
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
