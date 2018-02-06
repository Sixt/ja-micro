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

package com.sixt.service.framework.rpc;

import com.google.common.primitives.Ints;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.sixt.service.framework.FeatureFlags;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.injection.ServiceRegistryModule;
import com.sixt.service.framework.injection.TracingModule;
import com.sixt.service.framework.protobuf.FrameworkTest;
import com.sixt.service.framework.protobuf.RpcEnvelope;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpFields;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class RpcClientIntegrationTest {

    private final static String serviceName = "com.sixt.xxx";
    private final String featureFlag;
    private RpcClientFactory clientFactory;
    private LoadBalancerFactory loadBalancerFactory;
    private RpcClient<FrameworkTest.Foobar> rpcClient;
    private LoadBalancerImpl loadBalancer;
    private MockHttpClient httpClient;
    private ServiceDependencyHealthCheck dependencyHealthCheck;
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);

    @Parameterized.Parameters
    public static Collection<Object> data() {
        return Arrays.asList(new Object[] { "true", "false" });
    }

    public RpcClientIntegrationTest(String featureFlag) {
        this.featureFlag = featureFlag;
        TestInjectionModule module = new TestInjectionModule(featureFlag);
        ServiceProperties props = new ServiceProperties();
        props.addProperty(FeatureFlags.FLAG_EXPOSE_ERRORS_HTTP, featureFlag);
        props.addProperty(FeatureFlags.DISABLE_RPC_INSTANCE_RETRY, "true");
        props.addProperty(ServiceProperties.REGISTRY_SERVER_KEY, "localhost:65432");
        props.addProperty("registry", "consul");
        module.setServiceProperties(props);
        Injector injector = Guice.createInjector(module, new ServiceRegistryModule(props), new TracingModule(props));
        httpClient = (MockHttpClient)injector.getInstance(HttpClient.class);
        clientFactory = injector.getInstance(RpcClientFactory.class);
        loadBalancerFactory = injector.getInstance(LoadBalancerFactory.class);
        rpcClient = clientFactory.newClient(serviceName, "testing", FrameworkTest.Foobar.class).build();
        loadBalancer = (LoadBalancerImpl) loadBalancerFactory.getLoadBalancer(serviceName);
        dependencyHealthCheck = mock(ServiceDependencyHealthCheck.class);
    }

    @Before
    public void setup() {
        System.out.println("Running with " + FeatureFlags.FLAG_EXPOSE_ERRORS_HTTP + " = " + featureFlag);
    }

    @Test
    public void basicHappyPath() throws Exception {
        //Create 2 healthy endpoints and send 4 requests.
        //Each endpoint should have processed 2 requests each.
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));

        for (int i = 0; i < 4; i++) {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        }

        assertThat(httpClient.verifyRequestsProcessed(2, "localhost:20001")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(2, "localhost:20002")).isTrue();
    }

    @Test
    public void noInstances() {
        //no service endpoints available
        int errorCount = 0;
        try {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        } catch (RpcCallException ex) {
            errorCount++;
            assertThat(ex.getCategory()).isEqualTo(RpcCallException.Category.InternalServerError);
        }
        assertThat(errorCount).isEqualTo(1);
    }

    @Test
    public void newServiceInstanceAppears() throws Exception {
        //Create 2 servers and send 4 requests.  Each should have processed 2 requests each.
        //Add a new server.  Send 3 more requests.  Two servers should have processed 3
        //and the third one just 1.
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));
        for (int i = 0; i < 4; i++) {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        }

        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20003", "dc1", dependencyHealthCheck));
        for (int i = 0; i < 3; i++) {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        }

        assertThat(httpClient.verifyRequestsProcessed(3, "localhost:20001")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(3, "localhost:20002")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20003")).isTrue();
    }

    @Test
    public void singleServiceZeroRetries() throws Exception {
        //Verify retry behavior.  Create 1 server and have it always fail.
        //Set retries to 0.  Send a request, and verify server got issued one request.
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        rpcClient = clientFactory.newClient(serviceName, "testing", FrameworkTest.Foobar.class).
                withRetries(0).build();
        rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());

        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20001")).isTrue();
    }

    @Test
    public void multipleServicesSingleRetry() {
        //Create 2 servers and have them always fail.  Set retries to 1.  Send request.
        //Verify each server got issued one request.
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));
        httpClient.makeFailing();

        int failureCount = 0;
        try {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        } catch (java.lang.Exception e) {
            failureCount++;
        }
        assertThat(failureCount).isEqualTo(1);
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20001")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20002")).isTrue();
    }

    @Test
    public void allFailingNoInstanceAvailable() {
        //Create 3 servers and have them always fail.  Set retries to 4.  Send request.
        //Each server should be tried once, then the request should fail.
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20003", "dc1", dependencyHealthCheck));
        rpcClient = clientFactory.newClient(serviceName, "testing", FrameworkTest.Foobar.class).
                withRetries(4).build();
        httpClient.makeFailing();

        int failureCount = 0;
        try {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        } catch (java.lang.Exception e) {
            e.printStackTrace();
            failureCount++;
        }
        assertThat(failureCount).isEqualTo(1);
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20001")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20002")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20003")).isTrue();
    }

    @Test
    public void singleInstanceTimesOut() {
        //Create 2 servers and have one timeout.  Set retries to 1.  Send request.
        //The response from the 2nd should be returned.
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));
        rpcClient = clientFactory.newClient(serviceName, "testing", FrameworkTest.Foobar.class).
                withRetries(1).build();
        httpClient.makeFirstRequestTimeout();

        try {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20001")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20002")).isTrue();
    }

    @Test
    public void allInstancesTimeOut() {
        //Create 2 servers and have both timeout.  Set retries to 1.  Send request.
        //The response from the 2nd should be returned.
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));
        rpcClient = clientFactory.newClient(serviceName, "testing", FrameworkTest.Foobar.class).
                withRetries(1).build();
        httpClient.makeRequestsTimeout();

        int failureCount = 0;
        try {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        } catch (RpcCallException ex) {
            failureCount++;
            assertThat(ex.getCategory()).isEqualTo(RpcCallException.Category.RequestTimedOut);
        }
        assertThat(failureCount).isEqualTo(1);
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20001")).isTrue();
        assertThat(httpClient.verifyRequestsProcessed(1, "localhost:20002")).isTrue();
    }

    @Test
    public void nonRetriableError() {
        //Create 2 servers and have the first throw an exception that is not retriable.
        //The exception should be thrown and not retried with the other node
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));
        rpcClient = clientFactory.newClient(serviceName, "testing", FrameworkTest.Foobar.class).
                withRetries(1).build();
        httpClient.setResponseException(new RpcCallException(RpcCallException.Category.
                InsufficientPermissions, "test").withSource("testing"));

        int failureCount = 0;
        try {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        } catch (RpcCallException ex) {
            failureCount++;
            assertThat(ex.getCategory()).isEqualTo(RpcCallException.Category.InsufficientPermissions);
            assertThat(ex.getMessage()).isEqualTo("test");
        }
        assertThat(failureCount).isEqualTo(1);
        assertThat(httpClient.verifyRequestsProcessed(1)).isTrue();
    }

    @Test
    public void retriableError() {
        //Create 2 servers and have both throw an exception that is retriable.
        //The exception should be thrown locally
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20001", "dc1", dependencyHealthCheck));
        loadBalancer.addServiceEndpoint(new ServiceEndpoint(executor, "localhost:20002", "dc1", dependencyHealthCheck));
        rpcClient = clientFactory.newClient(serviceName, "testing", FrameworkTest.Foobar.class).
                withRetries(1).build();
        httpClient.setResponseException(new RpcCallException(RpcCallException.Category.
                InternalServerError, "test1234").withSource("testing567"));

        int failureCount = 0;
        try {
            rpcClient.callSynchronous(FrameworkTest.Foobar.newBuilder().build(), new OrangeContext());
        } catch (RpcCallException ex) {
            failureCount++;
            assertThat(ex.getCategory()).isEqualTo(RpcCallException.Category.InternalServerError);
            assertThat(ex.getMessage()).isEqualTo("test1234");
            assertThat(ex.getSource()).isEqualTo("testing567");
        }
        assertThat(failureCount).isEqualTo(1);
        assertThat(httpClient.verifyRequestsProcessed(2)).isTrue();
    }

}

class TestInjectionModule extends AbstractModule {

    private ServiceProperties serviceProperties;
    private HttpClient httpClient;

    public TestInjectionModule(String featureFlag) {
        httpClient = new MockHttpClient(featureFlag);
    }

    @Override
    protected void configure() {
        bind(HttpClient.class).toInstance(httpClient);
        bind(ServiceProperties.class).toInstance(serviceProperties);
        bind(LoadBalancer.class).to(LoadBalancerImpl.class);
    }

    @Provides
    public ExecutorService getExecutorService() {
        return Executors.newCachedThreadPool();
    }

    public void setServiceProperties(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

}

class MockHttpClient extends HttpClient {
    private final String featureFlag;
    private ContentResponse httpResponse;
    private List<String> requests = new ArrayList<>();
    private boolean requestsFail = false;
    private boolean isFirstRequestTimeout = false;
    private boolean requestsTimeout = false;
    private RpcCallException responseException = null;

    public MockHttpClient(String featureFlag) {
        super();
        this.featureFlag = featureFlag;
        initialize();
    }

    private void initialize() {
        httpResponse = mock(ContentResponse.class);
        when(httpResponse.getHeaders()).thenReturn(new HttpFields());
        when(httpResponse.getStatus()).thenReturn(200);
        try {
            RpcEnvelope.Response.Builder responseBuilder = RpcEnvelope.Response.newBuilder();
            responseBuilder.setServiceMethod("Test.test");
            if (requestsFail) {
                RpcCallException callException = new RpcCallException(
                        RpcCallException.Category.InternalServerError, "requests fail!");
                responseBuilder.setError(callException.toJson().toString());
            }
            RpcEnvelope.Response rpcResponse = responseBuilder.build();
            byte[] responseHeader = rpcResponse.toByteArray();
            byte[] payload = FrameworkTest.Foobar.newBuilder().build().toByteArray();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(Ints.toByteArray(responseHeader.length));
            out.write(responseHeader);
            out.write(Ints.toByteArray(payload.length));
            out.write(payload);
            out.flush();
            when(httpResponse.getContent()).thenReturn(out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Request newRequest(URI uri) {
        return newRequest(uri.toString());
    }

    @Override
    public Request newRequest(String uri) {
        Request retval = mock(Request.class);
        try {
            if (requestsTimeout || (isFirstRequestTimeout && requests.isEmpty())) {
                when(retval.send()).thenThrow(new TimeoutException());
            } else {
                when(retval.send()).thenReturn(httpResponse);
            }
            if (requestsFail && featureFlag.equals("true")) {
                when(httpResponse.getStatus()).thenReturn(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else if (responseException != null) {
                when(httpResponse.getStatus()).thenReturn(responseException.getCategory().getHttpStatus());
            } else {
                when(httpResponse.getStatus()).thenReturn(HttpServletResponse.SC_OK);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        when(retval.method(anyString())).thenReturn(retval);
        when(retval.content(any(ContentProvider.class))).thenReturn(retval);
        when(retval.timeout(anyLong(), any(TimeUnit.class))).thenReturn(retval);
        requests.add(uri);
        return retval;
    }

    public void makeFailing() {
        requestsFail = true;
        initialize();
    }

    public boolean verifyRequestsProcessed(int count, String target) {
        int retval = 0;
        for (String str : requests) {
            if (str.contains(target)) {
                retval++;
            }
        }
        if (retval != count) {
            System.out.println("Found count was " + retval);
        }
        return retval == count;
    }

    public void makeFirstRequestTimeout() {
        isFirstRequestTimeout = true;
    }

    public void makeRequestsTimeout() {
        requestsTimeout = true;
    }

    public void setResponseException(RpcCallException responseException) {
        this.responseException = responseException;
        if (featureFlag.equals("true")) {
            when(httpResponse.getStatus()).thenReturn(responseException.getCategory().getHttpStatus());
        }
        String response = "{\"error\":" + responseException.toJson() + ",\"result\":{}}";
        when(httpResponse.getContentAsString()).thenReturn(response);
        RpcEnvelope.Response pbResponse = RpcEnvelope.Response.newBuilder().
                setError(responseException.toJson().toString()).build();
        byte[] responseArray = pbResponse.toByteArray();
        byte[] headerLength = Ints.toByteArray(responseArray.length);
        byte[] bodyLength = Ints.toByteArray(0);
        byte[] overallPayload = concatAll(headerLength, responseArray, bodyLength);
        when(httpResponse.getContent()).thenReturn(overallPayload);
    }

    public static byte[] concatAll(byte[] first, byte[]... rest) {
        int totalLength = first.length;
        for (byte[] array : rest) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (byte[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    public boolean verifyRequestsProcessed(int count) {
        int found = 0;
        for (String request : requests) {
            if (! request.contains("/v1/")) { //ignore consul requests
                found++;
            }
        }
        return found == count;
    }
}
