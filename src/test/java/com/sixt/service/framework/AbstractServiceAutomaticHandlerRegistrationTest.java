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
import com.sixt.service.framework.annotation.RpcHandler;
import com.sixt.service.framework.rpc.RpcCallException;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.junit.Test;

import java.io.PrintStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractServiceAutomaticHandlerRegistrationTest {

    @Test
    public void it_should_find_handlers() throws Exception {
        TestService service = new TestService();
        service.initializeGuice();

        ClassInfoList rpcHandlers;
        ScanResult scanResult = new ClassGraph().enableAllInfo()
                .whitelistPackages("com.sixt.service.framework").scan();
        rpcHandlers = scanResult.getClassesWithAnnotation(RpcHandler.class.getName());
        service.registerMethodHandlers(rpcHandlers);

        Map<String, ServiceMethodHandler<? extends Message, ? extends Message>> s = service.getMethodHandlers();
        assertThat(s.size() == 2);
        assertThat(s.containsKey("Test.handler1"));
        assertThat(s.containsKey("Test.handler2"));
        assertThat(s.get("Test.handler1").getClass().equals(TestHandler.class));
        assertThat(s.get("Test.handler2").getClass().equals(TestHandler2.class));
    }

    @RpcHandler("Test.handler1")
    private static class TestHandler implements ServiceMethodHandler {

        @Override
        public Message handleRequest(Message request, OrangeContext ctx) throws RpcCallException {
            return null;
        }
    }

    @RpcHandler("Test.handler2")
    private static class TestHandler2 implements ServiceMethodHandler<Message, Message> {

        @Override
        public Message handleRequest(Message request, OrangeContext ctx) {
            return null;
        }
    }

    class TestService extends AbstractService {
        @Override
        public void registerMethodHandlers() {}

        @Override
        public void displayHelp(PrintStream out) {}
    }

}


