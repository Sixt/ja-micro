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

import com.sixt.service.framework.OrangeContext;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.slf4j.MDC;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class HttpRequestWrapper {

    private URI uri;
    private String method;
    private ServiceEndpoint instance;
    private ContentProvider contentProvider;
    private Map<String, String> headers = new HashMap<>();

    public HttpRequestWrapper(String method, ServiceEndpoint instance) {
        this.method = method;
        this.instance = instance;

        setUri(URI.create("http://" + instance.getHostAndPort() + "/"));
        setHeader("X-Correlation-Id", MDC.get(OrangeContext.CORRELATION_ID));
    }

    public Request newRequest(HttpClient httpClient) {
        Request request = httpClient.newRequest(uri);
        request.content(contentProvider).method(method);

        for (String key : headers.keySet()) {
            if (! "User-Agent".equals(key)) {
                request.header(key, headers.get(key));
            }
        }

        return request;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public String getMethod() {
        return method;
    }

    public ServiceEndpoint getServiceEndpoint() {
        return instance;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public void setContentProvider(ContentProvider contentProvider) {
        this.contentProvider = contentProvider;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public ContentProvider getContentProvider() {
        return contentProvider;
    }
}
