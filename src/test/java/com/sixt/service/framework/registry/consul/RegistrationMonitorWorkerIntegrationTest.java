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

package com.sixt.service.framework.registry.consul;

import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.LoadBalancerUpdate;
import com.sixt.service.framework.util.Sleeper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpFields;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static com.sixt.service.framework.registry.consul.RegistrationMonitorWorker.CONSUL_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class RegistrationMonitorWorkerIntegrationTest {

    private RegistrationMonitorWorker worker;
    private ServiceProperties props;
    private ContentResponse response;
    private String healthInfo = "[{\"Checks\":[{\"Status\":\"passing\"}],\"Service\":{\"ID\":\"xxx\",\"" +
            "Port\":2,\"Address\":\"1.1.1.1\"}}]";
    private String emptyHealthInfo = "[]";

    @Before
    public void setup() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        response = mock(ContentResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(response.getContentAsString()).thenReturn(healthInfo);
        HttpFields headers = new HttpFields();
        headers.add(CONSUL_INDEX, "42");
        when(response.getHeaders()).thenReturn(headers);
        Request request = mock(Request.class);
        when(httpClient.newRequest(anyString())).thenReturn(request);
        when(request.send()).thenReturn(response);
        props = new ServiceProperties();
        props.addProperty(ServiceProperties.REGISTRY_SERVER_KEY, "localhost:1234");
        worker = new RegistrationMonitorWorker(httpClient, props);
        worker.setServiceName("foobar");
    }

    @Test
    public void getRegistrationUris() {
        props.addProperty(ServiceProperties.REGISTRY_SERVER_KEY, "localhost:1234");
        assertThat(worker.getServiceHealthUri().toString()).isEqualTo(
                "http://localhost:1234/v1/health/service/foobar?stale");
        worker.consulIndex = "69";
        assertThat(worker.getServiceHealthUri().toString()).isEqualTo(
                "http://localhost:1234/v1/health/service/foobar?stale&index=69");
    }

    @Test(expected = IllegalStateException.class)
    public void noServiceNameSet() {
        worker.setServiceName(null);
        worker.run();
    }

    @Test
    public void requestHealthListSingleInstance() {
        when(response.getContentAsString()).thenReturn(healthInfo);
        List<ConsulHealthEntry> entries = worker.loadCurrentHealthList();
        assertThat(entries.size()).isEqualTo(1);
    }

    @Test
    public void problemGettingHealthInfo() {
        when(response.getStatus()).thenReturn(404);
        List<ConsulHealthEntry> entries = worker.loadCurrentHealthList();
        assertThat(entries).isEmpty();
        when(response.getStatus()).thenReturn(0);
        entries = worker.loadCurrentHealthList();
        assertThat(entries).isEmpty();
    }

    @Test
    public void initialServiceReported() {
        List<ConsulHealthEntry> list = new ArrayList<>();
        ConsulHealthEntry entry = new ConsulHealthEntry("1", ConsulHealthEntry.Status.Passing, "1.1.1.1", 1);
        list.add(entry);
        LoadBalancer lb = mock(LoadBalancer.class);
        worker.setLoadbalancer(lb);
        worker.reportInitialServicesList(list);
        assertThat(worker.discoveredServices).hasSize(1);
        verify(lb, times(1)).updateServiceEndpoints(any(LoadBalancerUpdate.class));
    }

    @Test
    public void serviceAppearsAfterStartup() throws Exception {
        //there was a bug where if a consumed service wasn't available at startup,
        //even when it came online later, it wouldn't be tracked.

        String emptyHealthInfo = "[]";
        when(response.getContentAsString()).thenReturn(emptyHealthInfo).
                thenReturn(healthInfo);

        LoadBalancer lb = mock(LoadBalancer.class);
        worker.setLoadbalancer(lb);
        worker.shutdownSemaphore.release();
        worker.sleeper = mock(Sleeper.class);

        worker.run();
        ArgumentCaptor<LoadBalancerUpdate> captor = ArgumentCaptor.forClass(LoadBalancerUpdate.class);
        verify(lb, times(1)).updateServiceEndpoints(captor.capture());
        assertThat(captor.getAllValues()).hasSize(1);
        LoadBalancerUpdate secondUpdate = captor.getAllValues().get(0);
        assertThat(secondUpdate.getNewServices()).isNotEmpty();
    }

    @Test
    public void serviceFlappingAfterStartup() throws Exception {
        //there was a bug where if a consumed service went down and came back, there
        //were extra and incorrect updates posted to the lb
        //1. is available  2. becomes unavail  3. becomes avail again
        when(response.getContentAsString()).thenReturn(healthInfo).thenReturn(emptyHealthInfo).
                then((Answer<String>) invocation -> {
                    worker.shutdownSemaphore.release();
                    return healthInfo;
                });

        LoadBalancer lb = mock(LoadBalancer.class);
        worker.setLoadbalancer(lb);
        worker.sleeper = mock(Sleeper.class);

        worker.run();
        ArgumentCaptor<LoadBalancerUpdate> captor = ArgumentCaptor.forClass(LoadBalancerUpdate.class);
        verify(lb, times(3)).updateServiceEndpoints(captor.capture());
        assertThat(captor.getAllValues()).hasSize(3);
    }

    //    @Test
//    public void newGoInstancesStartUnhealthy() throws UnsupportedEncodingException {
//        //TODO: determine if this is a bug with the go services are registering,
//        //      or if it is inherent in the consul registration process and
//        //      deprecate the workaround (or give a warning)
//
//        StringEntity firstServiceInfo = new StringEntity("[{\"ServiceID\":\"xxx\"," +
//                "\"ServiceAddress\":\"1.1.1.1\",\"ServicePort\":2,\"ModifyIndex\":3}]");
//        StringEntity secondServiceInfo = new StringEntity("[{\"ServiceID\":\"xyz\"," +
//                "\"ServiceAddress\":\"1.1.1.1\",\"ServicePort\":3,\"ModifyIndex\":3}]");
//        StringEntity emptyHealthInfo = new StringEntity("[]");
//        StringEntity firstHealthInfo = new StringEntity("[{\"ServiceID\":\"xxx\",\"" +
//                "Status\":\"passing\"}]");
//        StringEntity secondUnhealhy = new StringEntity("[{\"ServiceID\":\"xyz\",\"" +
//                "Status\":\"critical\"}]");
//        StringEntity secondHealthy = new StringEntity("[{\"ServiceID\":\"xyz\",\"" +
//                "Status\":\"passing\"}]");
//        when(response.getEntity()).thenReturn(firstServiceInfo).thenReturn(firstHealthInfo).
//                thenReturn(firstHealthInfo).thenReturn(firstHealthInfo).thenReturn(emptyHealthInfo).
//                thenReturn(secondUnhealhy).thenReturn(secondServiceInfo).
//                thenReturn(secondHealthy).
//                then((Answer<StringEntity>) invocation -> {
//                    worker.shutdownSemaphore.release();
//                    return secondHealthy;
//                });
//
//        LoadBalancer lb = mock(LoadBalancerImpl.class);
//        worker.setLoadbalancer(lb);
//        //worker.shutdownSemaphore.release();
//        worker.sleeper = mock(Sleeper.class);
//
//        worker.run();
//        ArgumentCaptor<LoadBalancerUpdate> captor = ArgumentCaptor.forClass(LoadBalancerUpdate.class);
//        verify(lb, atLeast(3)).updateServiceEndpoints(captor.capture()); //should be only 3
//        List<LoadBalancerUpdate> sentUpdates = captor.getAllValues();
//        assertThat(sentUpdates).hasSize(3);
//    }

    @Test
    public void verifyMainLoop() {
        LoadBalancer lb = mock(LoadBalancer.class);
        worker.setLoadbalancer(lb);
        worker.shutdownSemaphore.release();
        worker.sleeper = mock(Sleeper.class);
        when(response.getContentAsString()).thenReturn(null).thenReturn(healthInfo).thenReturn(healthInfo);
        worker.run();
        verify(lb, times(1)).updateServiceEndpoints(any(LoadBalancerUpdate.class));
    }
}
