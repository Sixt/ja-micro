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

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodPreHook;
import com.sixt.service.framework.rpc.RpcCallException;
import com.sixt.service.test_service.api.TestServiceOuterClass.GetRandomStringQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomHandlerPreHook implements ServiceMethodPreHook<GetRandomStringQuery> {

    private static final Logger logger = LoggerFactory.getLogger(RandomHandlerPreHook.class);

    @Override
    public GetRandomStringQuery handleRequest(GetRandomStringQuery request, OrangeContext ctx) throws RpcCallException {
        logger.info("Pre-hook for GetRandomStringQuery");
        return GetRandomStringQuery.newBuilder().setInput("PRE:" + request.getInput()).build();
    }

}