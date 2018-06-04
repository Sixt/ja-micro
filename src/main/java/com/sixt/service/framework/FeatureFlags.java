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

import org.apache.commons.lang3.StringUtils;

public class FeatureFlags {

    // Key for service property.  in seconds
    public final static String HEALTH_CHECK_POLL_INTERVAL = "healthCheckPollInterval";
    public final static int DEFAULT_HEALTH_CHECK_POLL_INTERVAL = 10;

    // Set to "true" to allow an RpcResponse to set the http response code, otherwise: always return 200.
    public final static String FLAG_EXPOSE_ERRORS_HTTP = "exposeErrorsHttp";
    public static boolean shouldExposeErrorsToHttp(ServiceProperties serviceProps) {
        String value = serviceProps.getProperty(FLAG_EXPOSE_ERRORS_HTTP);
        if (StringUtils.isNotEmpty(value) && Boolean.valueOf(value)) {
            return true;
        } else {
            return false;
        }
    }

    // If there is a failure making an RPC request, normally the request is retried
    // against other instances if the retry count allow it (and the type of error allows
    // for retries).  Set to "true" to ensure that the same request isn't resent to
    // an instance multiple times.
    // Be aware that this can cause RpcCallException: No available instance...
    public final static String DISABLE_RPC_INSTANCE_RETRY = "disableRpcInstanceRetry";
    public static boolean shouldDisableRpcInstanceRetry(ServiceProperties serviceProps) {
        String value = serviceProps.getProperty(DISABLE_RPC_INSTANCE_RETRY);
        if (StringUtils.isNotEmpty(value) && Boolean.valueOf(value)) {
            return true;
        } else {
            return false;
        }
    }

    public final static String HTTP_CONNECT_TIMEOUT = "httpConnectTimeout";
    public final static int DEFAULT_HTTP_CONNECT_TIMEOUT = 100;
    public static int getHttpConnectTimeout(ServiceProperties serviceProps) {
        return serviceProps.getIntegerProperty(HTTP_CONNECT_TIMEOUT,
                DEFAULT_HTTP_CONNECT_TIMEOUT);
    }

    public final static String HTTP_ADDRESS_RESOLUTION_TIMEOUT = "httpAddressResolutionTimeout";
    public final static int DEFAULT_HTTP_ADDRESS_RESOLUTION_TIMEOUT = 200;
    public static int getHttpAddressResolutionTimeout(ServiceProperties serviceProps) {
        return serviceProps.getIntegerProperty(HTTP_ADDRESS_RESOLUTION_TIMEOUT,
                DEFAULT_HTTP_ADDRESS_RESOLUTION_TIMEOUT);
    }

    public final static String READINESS_CHECK_PORT = "readinessCheckPort";
    public final static int DEFAULT_READINESS_CHECK_PORT = -1;
    public static int getReadinessCheckPort(ServiceProperties serviceProps) {
        return serviceProps.getIntegerProperty(READINESS_CHECK_PORT,
                DEFAULT_READINESS_CHECK_PORT);
    }

    public final static String MIN_JETTY_THREADS = "minJettyThreads";
    public final static int DEFAULT_MIN_JETTY_THREADS = 2;
    public static int getMinJettyThreads(ServiceProperties serviceProps) {
        return serviceProps.getIntegerProperty(MIN_JETTY_THREADS,
                DEFAULT_MIN_JETTY_THREADS);
    }

    public final static String MAX_JETTY_THREADS = "maxJettyThreads";
    public final static int DEFAULT_MAX_JETTY_THREADS = 64;
    public static int getMaxJettyThreads(ServiceProperties serviceProps) {
        return serviceProps.getIntegerProperty(MAX_JETTY_THREADS,
                DEFAULT_MAX_JETTY_THREADS);
    }

    public final static String KAFKA_MAX_BLOCK_MS = "kafkaMaxBlockMs";
    public final static int DEFAULT_KAFKA_MAX_BLOCK_MS = 850;
    public static int getKafkaMaxBlockMs(ServiceProperties serviceProps) {
        return serviceProps.getIntegerProperty(KAFKA_MAX_BLOCK_MS,
                DEFAULT_KAFKA_MAX_BLOCK_MS);
    }

    public final static String KAFKA_REQUEST_TIMEOUT_MS = "kafkaRequestTimeoutMs";
    public final static int DEFAULT_KAFKA_REQUEST_TIMEOUT_MS = 200;
    public static int getKafkaRequestTimeoutMs(ServiceProperties serviceProps) {
        return serviceProps.getIntegerProperty(KAFKA_REQUEST_TIMEOUT_MS,
                DEFAULT_KAFKA_REQUEST_TIMEOUT_MS);
    }

}
