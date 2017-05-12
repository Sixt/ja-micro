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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.protobuf.Message;

/**
 * The starting point for getting an RpcClient to interact with remote services
 */
@Singleton
public class RpcClientFactory {

    protected Injector injector;

    @Inject
    public RpcClientFactory(Injector injector) {
        this.injector = injector;
    }

    /**
     * Build an RpcClientBuilder.  Use the withXXX methods to customize the behavior
     * of the client, then finish with builder.build().
     */
    public <RESPONSE extends Message> RpcClientBuilder<RESPONSE> newClient(String serviceName, String methodName,
                                      Class<RESPONSE> responseClass) {
        @SuppressWarnings("unchecked")
        RpcClientBuilder<RESPONSE> retval = (RpcClientBuilder<RESPONSE>) injector.getInstance(RpcClientBuilder.class);
        retval.setServiceName(serviceName);
        retval.setMethodName(methodName);
        retval.setResponseClass(responseClass);
        return retval;
    }

}
