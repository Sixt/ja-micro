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

package com.sixt.service.test_service.handler;

import com.google.inject.Singleton;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.annotation.HealthCheckProvider;
import com.sixt.service.framework.annotation.RpcHandler;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.health.HealthCheckContributor;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.test_service.api.TestServiceOuterClass.SetHealthCheckStatusCommand;
import com.sixt.service.test_service.api.TestServiceOuterClass.SetHealthCheckStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@RpcHandler("TestService.SetHealthCheckStatus")
@HealthCheckProvider
public class SetHealthCheckStatusHandler implements ServiceMethodHandler<SetHealthCheckStatusCommand,
        SetHealthCheckStatusResponse>, HealthCheckContributor {

    private static final Logger logger = LoggerFactory.getLogger(SetHealthCheckStatusHandler.class);

    private HealthCheck currentHealth;

    public SetHealthCheckStatusHandler() {
        this.currentHealth = new HealthCheck("test_servlet",
                HealthCheck.Status.PASS, null);
    }

    @Override
    public SetHealthCheckStatusResponse handleRequest(SetHealthCheckStatusCommand request,
                                                      OrangeContext ctx) throws RpcCallException {
        try {
            HealthCheck.Status status = HealthCheck.Status.valueOf(request.getStatus());
            currentHealth = new HealthCheck("test_servlet", status, request.getMessage());
        } catch (Exception ex) {
            logger.warn("Caught exception", ex);
        }
        return SetHealthCheckStatusResponse.getDefaultInstance();
    }

    @Override
    public HealthCheck getHealthCheck() {
        HealthCheck retval = currentHealth;
        currentHealth = new HealthCheck("test_servlet",
                HealthCheck.Status.PASS, null);
        return retval;
    }

    @Override
    public boolean shouldRegister() {
        return true;
    }

}
