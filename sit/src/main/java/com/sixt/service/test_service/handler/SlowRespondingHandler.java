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
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.framework.util.Sleeper;
import com.sixt.service.test_service.api.TestServiceOuterClass.GetRandomStringQuery;
import com.sixt.service.test_service.api.TestServiceOuterClass.RandomStringResponse;

@Singleton
public class SlowRespondingHandler implements ServiceMethodHandler<GetRandomStringQuery, RandomStringResponse> {

    @Override
    public RandomStringResponse handleRequest(GetRandomStringQuery request, OrangeContext ctx) throws RpcCallException {
        new Sleeper().sleepNoException(5000);
        return RandomStringResponse.getDefaultInstance();
    }

}
