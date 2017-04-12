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

package com.sixt.service.test_service;

import com.sixt.service.framework.AbstractService;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.annotation.OrangeMicroservice;
import com.sixt.service.framework.kafka.TopicVerification;
import com.sixt.service.framework.kafka.messaging.*;
import com.sixt.service.framework.util.Sleeper;
import com.sixt.service.test_service.handler.*;

import java.io.PrintStream;

@OrangeMicroservice
public class ServiceEntryPoint extends AbstractService {

    @Override
    public void registerMethodHandlers() {
        registerMethodHandlerFor("TestService.GetRandomString", RandomHandler.class);
        registerPreMethodHandlerHookFor("TestService.GetRandomString", RandomHandlerPreHook.class);
        registerPostMethodHandlerHookFor("TestService.GetRandomString", RandomHandlerPostHook.class);
        registerMethodHandlerFor("TestService.SlowResponder", SlowRespondingHandler.class);
    }

    @Override
    public void displayHelp(PrintStream out) {
    }


    @Override
    public void bootstrapComplete() throws InterruptedException {

        // TODO: wait for topic creation

        // Start a messaging consumer for the default inbox.
        ConsumerFactory consumerFactory = injector.getInstance(ConsumerFactory.class);
        consumerFactory.defaultInboxConsumer(new DiscardFailedMessages());

        injector.getInstance(RandomEventHandler.class);


        super.bootstrapComplete();
    }
}