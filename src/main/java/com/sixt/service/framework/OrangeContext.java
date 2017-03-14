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

import io.opentracing.SpanContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates context information needed for services to communicate, share
 * correlation IDs, get information about the original request, gives the
 * ability to pass arbitrary information to services in the service call chain, etc.
 */
public class OrangeContext {

    public final static String CORRELATION_ID = "correlation-id";
    private static final String RPC_ORIGIN_SERVICE = "X-Sx-From-Service";
    private static final String RPC_ORIGIN_METHOD = "X-Sx-From-Method";

    private String correlationId;
    private Map<String, String> properties = new HashMap<>();
    private SpanContext tracingContext;

    public OrangeContext() {
        this(null, null);
    }

    public OrangeContext(Map<String, String> props) {
        this(props == null ? null : props.get("x-correlation-id"), props);
    }

    public OrangeContext(String correlationId, Map<String, String> props) {
        if (correlationId == null) {
            this.correlationId = UUID.randomUUID().toString();
        } else {
            this.correlationId = correlationId;
        }
        if (props != null) {
            this.properties = props;
        }
    }

    public OrangeContext(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        if (correlationId != null && this.correlationId == null) {
            this.correlationId = correlationId;
        }
    }

    public String getRpcOriginService() {
        return getProperty(RPC_ORIGIN_SERVICE);
    }

    public String getRpcOriginMethod() {
        return getProperty(RPC_ORIGIN_METHOD);
    }

    public void setProperty(String key, String value) {
        properties.put(key.toLowerCase(), value);
    }

    public Map<String, String> getProperties() {
        return new HashMap<>(properties);
    }

    public String getProperty(String key) {
        return properties.get(key.toLowerCase());
    }

    public SpanContext getTracingContext() {
        return tracingContext;
    }

    public void setTracingContext(SpanContext tracingContext) {
        this.tracingContext = tracingContext;
    }

    //TODO: getIntProperty, getLongProperty, etc.
}
