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

import com.google.common.collect.ImmutableSet;
import com.sixt.service.framework.AbstractService;
import com.sixt.service.framework.annotation.OrangeMicroservice;
import com.sixt.service.framework.kafka.TopicVerification;
import com.sixt.service.framework.kafka.messaging.ConsumerFactory;
import com.sixt.service.framework.kafka.messaging.DiscardFailedMessages;
import com.sixt.service.framework.kafka.messaging.Topic;
import com.sixt.service.framework.util.Sleeper;
import com.sixt.service.test_service.handler.*;

import java.io.PrintStream;
import java.util.Set;

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

        // To avoid sporadic test failures, we need to ensure the topics are created before we start the test service.
        TopicVerification topicVerification = new TopicVerification();
        Sleeper sleeper = new Sleeper();
        String serviceName = serviceProperties.getServiceName();
        Topic defaultInbox = Topic.defaultServiceInbox(serviceName);

        Set<String> requiredTopics = ImmutableSet.of(defaultInbox.toString(), "events");
        while(! topicVerification.verifyTopicsExist(serviceProperties.getKafkaServer(), requiredTopics, false)) {
            sleeper.sleep(500);
        }

        // Start a messaging consumer for the default inbox.
        ConsumerFactory consumerFactory = injector.getInstance(ConsumerFactory.class);
        consumerFactory.defaultInboxConsumer(new DiscardFailedMessages());

        injector.getInstance(RandomEventHandler.class);

        super.bootstrapComplete();
    }
}