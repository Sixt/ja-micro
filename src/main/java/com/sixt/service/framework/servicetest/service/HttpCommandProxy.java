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

package com.sixt.service.framework.servicetest.service;

import com.google.inject.Inject;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.LoadBalancerFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;

public class HttpCommandProxy {
    
    protected LoadBalancerFactory lbFactory;
    protected HttpClient httpClient;
    private String serviceName;
    private String serviceBaseUrl;

    @Inject
    public HttpCommandProxy(LoadBalancerFactory lbFactory, HttpClient httpClient) {
        this.lbFactory = lbFactory;
        this.httpClient = httpClient;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String sendHttpGet(String path) throws Exception {
        return sendRequest(path, null, HttpMethod.GET);
    }

    public String sendHttpPut(String path, String data) throws Exception {
        return sendRequest(path, data, HttpMethod.PUT);
    }

    public String sendHttpDelete(String path, String data) throws Exception {
        return sendRequest(path, data, HttpMethod.DELETE);
    }

    public String sendHttpPost(String path, String data) throws Exception {
        return sendRequest(path, data, HttpMethod.POST);
    }

    protected String sendRequest(String path, String data, HttpMethod method) throws Exception {
        String url = getServiceUrl(path);
        Request request = httpClient.newRequest(url).method(method).
                header(HttpHeader.CONTENT_TYPE, "application/json");
        if (data != null) {
            request.content(new StringContentProvider(data));
        }
        ContentResponse response = request.send();
        return response.getContentAsString();
    }

    protected String getServiceUrl(String path) {
        String baseUrl = getServiceBaseUrl();
        return baseUrl + path;
    }

    private synchronized String getServiceBaseUrl() {
        if (serviceBaseUrl == null) {
            LoadBalancer lb = lbFactory.getLoadBalancer(serviceName);
            lb.waitForServiceInstance();
            com.sixt.service.framework.rpc.ServiceEndpoint endpoint = lb.getHealthyInstance();
            serviceBaseUrl = "http://" + endpoint.getHostAndPort();
        }
        return serviceBaseUrl;
    }

}
