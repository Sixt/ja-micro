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

import com.sixt.service.another_service.api.AnotherServiceOuterClass;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.annotation.RpcHandler;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.rpc.RpcClient;
import com.sixt.service.framework.rpc.RpcClientFactory;
import com.sixt.service.test_service.api.TestServiceOuterClass;
import com.sixt.service.test_service.api.TestServiceOuterClass.CallAnotherServiceCommand;
import com.sixt.service.test_service.api.TestServiceOuterClass.CallAnotherServiceResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@RpcHandler("TestService.CallsAnotherService")
public class CallsAnotherServiceHandler implements ServiceMethodHandler<CallAnotherServiceCommand,
        CallAnotherServiceResponse> {

    private RpcClient<CallAnotherServiceResponse> rpcClient;

    @Inject
    public CallsAnotherServiceHandler(RpcClientFactory factory) {
        rpcClient = factory.newClient("com.sixt.service.another-service",
                "AnotherService.ImpersonatorTest",
                CallAnotherServiceResponse.class).build();
    }

    @Override
    public CallAnotherServiceResponse handleRequest(CallAnotherServiceCommand command,
                                                    OrangeContext ctx) throws RpcCallException {
        rpcClient.callSynchronous(CallAnotherServiceCommand.getDefaultInstance(), ctx);
        return CallAnotherServiceResponse.getDefaultInstance();
    }

}
