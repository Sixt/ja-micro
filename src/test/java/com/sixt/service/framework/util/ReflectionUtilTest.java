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

package com.sixt.service.framework.util;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.protobuf.RpcEnvelope;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {

    @Test
    public void verifyReflection() throws ClassNotFoundException {
        TestServiceMethodHandler handler = new TestServiceMethodHandler();
        assertThat(ReflectionUtil.findSubClassParameterType(handler, 0)).
                isEqualTo(RpcEnvelope.Request.class);
        assertThat(ReflectionUtil.findSubClassParameterType(handler, 1)).
                isEqualTo(RpcEnvelope.Response.class);
        assertThat(ReflectionUtil.findSubClassParameterType(handler, 2)).isNull();
    }

    @Test
    public void verifyReflectionWorksWithAChild() throws ClassNotFoundException {
        ChildTestServiceMethodHandler handler = new ChildTestServiceMethodHandler();
        assertThat(ReflectionUtil.findSubClassParameterType(handler, 0)).
            isEqualTo(RpcEnvelope.Request.class);
        assertThat(ReflectionUtil.findSubClassParameterType(handler, 1)).
            isEqualTo(RpcEnvelope.Response.class);
        assertThat(ReflectionUtil.findSubClassParameterType(handler, 2)).isNull();
    }

    private class TestServiceMethodHandler implements
            ServiceMethodHandler<RpcEnvelope.Request, RpcEnvelope.Response> {

        @Override
        public RpcEnvelope.Response handleRequest(RpcEnvelope.Request request, OrangeContext ctx) {
            return null;
        }
    }

    private class ChildTestServiceMethodHandler
        extends TestServiceMethodHandler {

        @Override
        public RpcEnvelope.Response handleRequest(RpcEnvelope.Request request, OrangeContext ctx) {
            return null;
        }
    }
}
