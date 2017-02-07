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

import com.google.protobuf.Message;
import org.junit.Test;

import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractServiceTest {

    @Test
    public void methodHandlerShouldBeRegistered() throws Exception {
        // given
        AbstractServiceMock service = new AbstractServiceMock();
        service.initializeGuice();

        // when
        service.registerMethodHandlerFor("Test.methodHandlerShouldBeRegistered", ServiceMethodHandlerMock.class);

        // then
        assertThat(
                ServiceMethodHandlerMock.class.getName().equals(
                        service.getMethodHandlers().get("Test.methodHandlerShouldBeRegistered").getClass().getName()
                )
        );
    }

    private static class ServiceMethodHandlerMock implements ServiceMethodHandler {

        @Override
        public Message handleRequest(Message request, OrangeContext ctx) {
            return null;
        }

    }

    private class AbstractServiceMock extends AbstractService {

        @Override
        public void registerMethodHandlers() { }

        @Override
        public void displayHelp(PrintStream out) { }

    }

}
